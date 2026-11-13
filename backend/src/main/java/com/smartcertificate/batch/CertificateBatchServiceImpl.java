package com.smartcertificate.batch;

import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.media.MediaAssetService;
import com.smartcertificate.media.StorageService;
import com.smartcertificate.template.TemplateService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
class CertificateBatchServiceImpl implements CertificateBatchService {

    private static final int SAMPLE_PREVIEW_LIMIT = 5;
    private static final List<BatchItemStatus> ERROR_STATUSES =
            List.of(BatchItemStatus.INVALID, BatchItemStatus.FAILED);

    private final CertificateBatchRepository batchRepository;
    private final CertificateBatchItemRepository itemRepository;
    private final BatchRosterParser rosterParser;
    private final BatchRowMapperAndValidator rowValidator;
    private final TemplateService templateService;
    private final MediaAssetService mediaAssetService;
    private final StorageService storageService;
    private final AuditEventService auditEventService;
    private final BulkBatchAsyncWorker asyncWorker;

    CertificateBatchServiceImpl(
            CertificateBatchRepository batchRepository,
            CertificateBatchItemRepository itemRepository,
            BatchRosterParser rosterParser,
            BatchRowMapperAndValidator rowValidator,
            TemplateService templateService,
            MediaAssetService mediaAssetService,
            StorageService storageService,
            AuditEventService auditEventService,
            BulkBatchAsyncWorker asyncWorker) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.rosterParser = rosterParser;
        this.rowValidator = rowValidator;
        this.templateService = templateService;
        this.mediaAssetService = mediaAssetService;
        this.storageService = storageService;
        this.auditEventService = auditEventService;
        this.asyncWorker = asyncWorker;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BatchSummaryResponse> listBatches(
            UUID organizationId, UUID actorUserId, boolean canReadAll, Pageable pageable) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CertificateBatchEntity> page = canReadAll
                ? batchRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, sorted)
                : batchRepository.findByOrganizationIdAndCreatedByOrderByCreatedAtDesc(
                        organizationId, actorUserId, sorted);
        return page.map(this::toSummary);
    }

    @Override
    @Transactional
    public ImportOutcome importBatch(
            UUID organizationId, UUID actorUserId, UUID templateId, String idempotencyKey, MultipartFile file) {
        if (templateId == null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Template ID is required.",
                    Map.of("templateId", "Select a published template before uploading a roster."));
        }

        String normalizedKey = trimToNull(idempotencyKey);
        if (normalizedKey != null) {
            var existing = batchRepository.findByOrganizationIdAndIdempotencyKey(organizationId, normalizedKey);
            if (existing.isPresent()) {
                return new ImportOutcome(buildDetail(existing.get()), true);
            }
        }

        TemplateService.PublishedTemplateSnapshot templateSnapshot =
                templateService.getPublishedTemplateForIssuance(organizationId, templateId);

        BatchRosterParser.ParsedRoster parsed = rosterParser.parse(file);
        UUID sourceAssetId = mediaAssetService.storeBatchSourceFile(
                organizationId, actorUserId, parsed.normalizedFileName(), parsed.mimeType(), parsed.rawBytes());

        List<String> templatePlaceholders = rowValidator.extractTemplatePlaceholders(templateSnapshot.designJson());
        Map<String, String> suggestedMapping =
                rowValidator.suggestInitialMapping(parsed.headers(), templatePlaceholders);

        UUID batchId = UUID.randomUUID();
        CertificateBatchEntity batchEntity = new CertificateBatchEntity(
                batchId,
                organizationId,
                templateSnapshot.templateId(),
                templateSnapshot.templateVersionId(),
                parsed.rows().size(),
                sourceAssetId,
                parsed.normalizedFileName(),
                parsed.headers(),
                suggestedMapping,
                normalizedKey,
                actorUserId);

        try {
            CertificateBatchEntity savedBatch = batchRepository.saveAndFlush(batchEntity);

            List<CertificateBatchItemEntity> itemEntities =
                    new ArrayList<>(parsed.rows().size());
            for (int i = 0; i < parsed.rows().size(); i++) {
                itemEntities.add(new CertificateBatchItemEntity(
                        UUID.randomUUID(),
                        savedBatch.getId(),
                        i + 1,
                        parsed.rows().get(i)));
            }
            itemRepository.saveAll(itemEntities);

            auditEventService.recordEvent(
                    organizationId,
                    actorUserId,
                    "BATCH_IMPORTED",
                    null,
                    Map.of(
                            "batchId", savedBatch.getId().toString(),
                            "templateId", templateSnapshot.templateId().toString(),
                            "totalRecords", parsed.rows().size(),
                            "fileName", parsed.normalizedFileName()));

            return new ImportOutcome(buildDetailWithContext(savedBatch, templatePlaceholders, itemEntities), false);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedKey != null) {
                var raceWinner = batchRepository.findByOrganizationIdAndIdempotencyKey(organizationId, normalizedKey);
                if (raceWinner.isPresent()) {
                    return new ImportOutcome(buildDetail(raceWinner.get()), true);
                }
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BatchDetailResponse getBatch(UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId) {
        CertificateBatchEntity batch = requireAccessibleBatch(organizationId, actorUserId, canReadAll, batchId);
        return buildDetail(batch);
    }

    @Override
    @Transactional
    public BatchDetailResponse saveMapping(
            UUID organizationId,
            UUID actorUserId,
            boolean canReadAll,
            UUID batchId,
            BatchColumnMappingRequest request) {
        CertificateBatchEntity batch = requireAccessibleBatch(organizationId, actorUserId, canReadAll, batchId);
        ensureMutableForMapping(batch);

        Set<String> validHeaders = new LinkedHashSet<>(batch.getSourceHeaders());
        Map<String, String> cleanedMapping = new LinkedHashMap<>();
        if (request.mappings() != null) {
            for (Map.Entry<String, String> entry : request.mappings().entrySet()) {
                String targetField = trimToNull(entry.getKey());
                String sourceHeader = trimToNull(entry.getValue());
                if (targetField == null || sourceHeader == null) {
                    continue;
                }
                if (!validHeaders.contains(sourceHeader)) {
                    throw new AppException(
                            ErrorCode.VALIDATION_ERROR,
                            "Mapped source column '" + sourceHeader + "' does not exist in uploaded file headers.");
                }
                cleanedMapping.put(targetField, sourceHeader);
            }
        }

        if (!cleanedMapping.containsKey("recipientName") || !cleanedMapping.containsKey("courseName")) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Both recipientName and courseName must be mapped before saving.",
                    Map.of("mappings", "Map required fields: recipientName and courseName."));
        }

        batch.updateMapping(cleanedMapping);
        CertificateBatchEntity saved = batchRepository.save(batch);

        List<CertificateBatchItemEntity> items = itemRepository.findByBatchIdOrderByRowNumberAsc(batch.getId());
        for (CertificateBatchItemEntity item : items) {
            item.resetToPending();
        }
        itemRepository.saveAll(items);

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "BATCH_MAPPED",
                null,
                Map.of(
                        "batchId",
                        batchId.toString(),
                        "mappedFields",
                        cleanedMapping.keySet().size()));

        return buildDetail(saved);
    }

    @Override
    @Transactional
    public BatchDetailResponse validateBatch(UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId) {
        CertificateBatchEntity batch = requireAccessibleBatch(organizationId, actorUserId, canReadAll, batchId);
        ensureMutableForMapping(batch);

        Map<String, String> mapping = batch.getColumnMapping();
        if (mapping == null
                || trimToNull(mapping.get("recipientName")) == null
                || trimToNull(mapping.get("courseName")) == null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, "Map both recipientName and courseName before validating batch rows.");
        }

        List<CertificateBatchItemEntity> items = itemRepository.findByBatchIdOrderByRowNumberAsc(batch.getId());
        int validCount = 0;
        int invalidCount = 0;

        for (CertificateBatchItemEntity item : items) {
            if (item.getStatus() == BatchItemStatus.SUCCEEDED) {
                validCount++;
                continue;
            }
            BatchRowMapperAndValidator.RowOutcome outcome = rowValidator.validateAndBuildIssueRequest(
                    batch.getTemplateId(), mapping, item.getInputData(), item.getIdempotencyKey());
            if (outcome.valid()) {
                item.markValid();
                validCount++;
            } else {
                item.markInvalid(outcome.errorMessage());
                invalidCount++;
            }
        }

        itemRepository.saveAll(items);
        batch.markValidated(validCount, invalidCount);
        CertificateBatchEntity saved = batchRepository.save(batch);

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "BATCH_VALIDATED",
                null,
                Map.of("batchId", batchId.toString(), "validRecords", validCount, "invalidRecords", invalidCount));

        return buildDetail(saved);
    }

    @Override
    @Transactional
    public BatchDetailResponse generateBatch(UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId) {
        CertificateBatchEntity batch = requireAccessibleBatch(organizationId, actorUserId, canReadAll, batchId);

        if (batch.getStatus() == BatchStatus.QUEUED || batch.getStatus() == BatchStatus.PROCESSING) {
            throw new AppException(ErrorCode.CONFLICT, "This certificate batch is already queued or processing.");
        }
        if (batch.getStatus() == BatchStatus.COMPLETED || batch.getStatus() == BatchStatus.COMPLETED_WITH_ERRORS) {
            throw new AppException(
                    ErrorCode.CONFLICT, "This certificate batch has already completed. Use retry if any rows failed.");
        }

        // If user hits Generate directly after mapping, run validation if not yet validated.
        if (batch.getStatus() == BatchStatus.UPLOADED || batch.getStatus() == BatchStatus.MAPPED) {
            validateBatch(organizationId, actorUserId, canReadAll, batchId);
            batch = requireAccessibleBatch(organizationId, actorUserId, canReadAll, batchId);
        }

        long runnableCount = itemRepository.countByBatchIdAndStatus(batch.getId(), BatchItemStatus.VALID);
        if (runnableCount == 0) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "No valid rows are available to generate. Fix invalid rows or adjust column mapping.");
        }

        TemplateService.PublishedTemplateSnapshot templateSnapshot =
                templateService.getPublishedTemplateForIssuance(organizationId, batch.getTemplateId());

        batch.markQueued(templateSnapshot.templateVersionId());
        CertificateBatchEntity saved = batchRepository.save(batch);

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "BATCH_GENERATION_QUEUED",
                null,
                Map.of("batchId", batchId.toString(), "validRecords", runnableCount));

        triggerAsyncAfterCommit(saved.getId(), organizationId, actorUserId);
        return buildDetail(saved);
    }

    @Override
    @Transactional
    public BatchDetailResponse retryFailedItems(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId) {
        CertificateBatchEntity batch = requireAccessibleBatch(organizationId, actorUserId, canReadAll, batchId);
        if (batch.getStatus() == BatchStatus.QUEUED || batch.getStatus() == BatchStatus.PROCESSING) {
            throw new AppException(ErrorCode.CONFLICT, "Batch is currently running.");
        }

        List<CertificateBatchItemEntity> failedItems = itemRepository.findByBatchIdAndStatusInOrderByRowNumberAsc(
                batch.getId(), List.of(BatchItemStatus.FAILED));
        if (failedItems.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "No failed rows require retry in this batch.");
        }

        TemplateService.PublishedTemplateSnapshot templateSnapshot =
                templateService.getPublishedTemplateForIssuance(organizationId, batch.getTemplateId());

        for (CertificateBatchItemEntity item : failedItems) {
            item.markForRetry();
        }
        itemRepository.saveAll(failedItems);

        batch.markQueued(templateSnapshot.templateVersionId());
        CertificateBatchEntity saved = batchRepository.save(batch);

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "BATCH_RETRY_QUEUED",
                null,
                Map.of("batchId", batchId.toString(), "retryCount", failedItems.size()));

        triggerAsyncAfterCommit(saved.getId(), organizationId, actorUserId);
        return buildDetail(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BatchErrorItemResponse> listBatchErrors(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId, Pageable pageable) {
        CertificateBatchEntity batch = requireAccessibleBatch(organizationId, actorUserId, canReadAll, batchId);
        return itemRepository
                .findByBatchIdAndStatusInOrderByRowNumberAsc(batch.getId(), ERROR_STATUSES, pageable)
                .map(item -> new BatchErrorItemResponse(
                        item.getId(),
                        item.getRowNumber(),
                        item.getStatus(),
                        item.getErrorMessage(),
                        item.getInputData()));
    }

    @Override
    @Transactional
    public ZipDownloadPayload downloadBatchZip(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId) {
        CertificateBatchEntity batch = requireAccessibleBatch(organizationId, actorUserId, canReadAll, batchId);
        if (batch.getResultZipObjectKey() == null
                || batch.getResultZipObjectKey().isBlank()) {
            throw new AppException(ErrorCode.CONFLICT, "ZIP export is not yet available for this certificate batch.");
        }

        byte[] zipBytes = storageService.load(batch.getResultZipObjectKey());
        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "BATCH_ZIP_DOWNLOADED",
                null,
                Map.of("batchId", batch.getId().toString(), "successfulRecords", batch.getSuccessfulRecords()));

        return new ZipDownloadPayload(
                "certificates-batch-" + batch.getId().toString().substring(0, 8) + ".zip", "application/zip", zipBytes);
    }

    @Override
    @Transactional(readOnly = true)
    public BatchStats getBatchStats(UUID organizationId, UUID actorUserId, boolean canReadAll) {
        List<BatchStatus> activeStatuses = List.of(BatchStatus.QUEUED, BatchStatus.PROCESSING);
        if (canReadAll) {
            return new BatchStats(
                    batchRepository.countByOrganizationId(organizationId),
                    batchRepository.countByOrganizationIdAndStatusIn(organizationId, activeStatuses));
        }
        return new BatchStats(
                batchRepository.countByOrganizationIdAndCreatedBy(organizationId, actorUserId),
                batchRepository.countByOrganizationIdAndCreatedByAndStatusIn(
                        organizationId, actorUserId, activeStatuses));
    }

    private void triggerAsyncAfterCommit(UUID batchId, UUID organizationId, UUID actorUserId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncWorker.processBatchAsync(batchId, organizationId, actorUserId);
                }
            });
        } else {
            asyncWorker.processBatchAsync(batchId, organizationId, actorUserId);
        }
    }

    private CertificateBatchEntity requireAccessibleBatch(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId) {
        CertificateBatchEntity batch = batchRepository
                .findByIdAndOrganizationId(batchId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Certificate batch was not found."));
        if (!canReadAll && !batch.getCreatedBy().equals(actorUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "You do not have permission to access this batch.");
        }
        return batch;
    }

    private static void ensureMutableForMapping(CertificateBatchEntity batch) {
        BatchStatus status = batch.getStatus();
        if (status == BatchStatus.QUEUED
                || status == BatchStatus.PROCESSING
                || status == BatchStatus.COMPLETED
                || status == BatchStatus.COMPLETED_WITH_ERRORS) {
            throw new AppException(
                    ErrorCode.CONFLICT,
                    "Batch mapping and validation cannot be modified once processing has started or completed.");
        }
    }

    private BatchDetailResponse buildDetail(CertificateBatchEntity batch) {
        List<String> placeholders = resolveTemplatePlaceholders(batch.getOrganizationId(), batch.getTemplateId());
        Page<CertificateBatchItemEntity> samplePage =
                itemRepository.findByBatchIdOrderByRowNumberAsc(batch.getId(), PageRequest.of(0, SAMPLE_PREVIEW_LIMIT));
        return buildDetailWithContext(batch, placeholders, samplePage.getContent());
    }

    private List<String> resolveTemplatePlaceholders(UUID organizationId, UUID templateId) {
        try {
            TemplateService.PublishedTemplateSnapshot snapshot =
                    templateService.getPublishedTemplateForIssuance(organizationId, templateId);
            return rowValidator.extractTemplatePlaceholders(snapshot.designJson());
        } catch (AppException ex) {
            return rowValidator.extractTemplatePlaceholders(null);
        }
    }

    private BatchSummaryResponse toSummary(CertificateBatchEntity batch) {
        return new BatchSummaryResponse(
                batch.getId(),
                batch.getTemplateId(),
                batch.getTemplateVersionId(),
                batch.getSourceFileName(),
                batch.getStatus(),
                batch.getTotalRecords(),
                batch.getValidRecords(),
                batch.getInvalidRecords(),
                batch.getSuccessfulRecords(),
                batch.getFailedRecords(),
                batch.getResultZipObjectKey() != null
                        && !batch.getResultZipObjectKey().isBlank(),
                batch.getCreatedBy(),
                batch.getCreatedAt(),
                batch.getCompletedAt());
    }

    private BatchDetailResponse buildDetailWithContext(
            CertificateBatchEntity batch,
            List<String> templatePlaceholders,
            List<CertificateBatchItemEntity> sampleItems) {
        List<Map<String, String>> sampleRows = sampleItems.stream()
                .limit(SAMPLE_PREVIEW_LIMIT)
                .map(CertificateBatchItemEntity::getInputData)
                .toList();

        return new BatchDetailResponse(
                batch.getId(),
                batch.getOrganizationId(),
                batch.getTemplateId(),
                batch.getTemplateVersionId(),
                batch.getSourceFileName(),
                batch.getStatus(),
                batch.getTotalRecords(),
                batch.getValidRecords(),
                batch.getInvalidRecords(),
                batch.getSuccessfulRecords(),
                batch.getFailedRecords(),
                batch.getResultZipObjectKey() != null
                        && !batch.getResultZipObjectKey().isBlank(),
                batch.getSourceHeaders(),
                batch.getColumnMapping(),
                templatePlaceholders,
                sampleRows,
                batch.getIdempotencyKey(),
                batch.getErrorSummary(),
                batch.getCreatedBy(),
                batch.getCreatedAt(),
                batch.getCompletedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
