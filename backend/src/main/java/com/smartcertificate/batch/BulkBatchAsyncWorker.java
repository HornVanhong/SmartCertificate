package com.smartcertificate.batch;

import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.certificate.CertificateService;
import com.smartcertificate.certificate.IssueCertificateRequest;
import com.smartcertificate.media.StorageService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class BulkBatchAsyncWorker {

    private static final Logger log = LoggerFactory.getLogger(BulkBatchAsyncWorker.class);

    private final CertificateBatchRepository batchRepository;
    private final CertificateBatchItemRepository itemRepository;
    private final BatchRowMapperAndValidator rowValidator;
    private final CertificateService certificateService;
    private final StorageService storageService;
    private final AuditEventService auditEventService;
    private final TransactionTemplate transactionTemplate;

    BulkBatchAsyncWorker(
            CertificateBatchRepository batchRepository,
            CertificateBatchItemRepository itemRepository,
            BatchRowMapperAndValidator rowValidator,
            CertificateService certificateService,
            StorageService storageService,
            AuditEventService auditEventService,
            TransactionTemplate transactionTemplate) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.rowValidator = rowValidator;
        this.certificateService = certificateService;
        this.storageService = storageService;
        this.auditEventService = auditEventService;
        this.transactionTemplate = transactionTemplate;
    }

    @Async("bulkBatchExecutor")
    public void processBatchAsync(UUID batchId, UUID organizationId, UUID actorUserId) {
        try {
            processBatchInternal(batchId, organizationId, actorUserId);
        } catch (Exception ex) {
            log.error("Bulk batch {} failed unexpectedly", batchId, ex);
            transactionTemplate.executeWithoutResult(status -> batchRepository
                    .findByIdAndOrganizationId(batchId, organizationId)
                    .ifPresent(batch -> {
                        batch.markFinished(
                                BatchStatus.FAILED,
                                batch.getSuccessfulRecords(),
                                batch.getFailedRecords(),
                                batch.getResultZipObjectKey(),
                                truncateError(ex.getMessage()));
                        batchRepository.save(batch);
                        auditEventService.recordEvent(
                                organizationId,
                                actorUserId,
                                "BATCH_FAILED",
                                truncateError(ex.getMessage()),
                                Map.of("batchId", batchId.toString()));
                    }));
        }
    }

    private void processBatchInternal(UUID batchId, UUID organizationId, UUID actorUserId) {
        BatchExecutionContext ctx = transactionTemplate.execute(status -> {
            CertificateBatchEntity batch = batchRepository
                    .findByIdAndOrganizationId(batchId, organizationId)
                    .orElse(null);
            if (batch == null
                    || (batch.getStatus() != BatchStatus.QUEUED && batch.getStatus() != BatchStatus.PROCESSING)) {
                return null;
            }
            batch.markProcessing();
            batchRepository.save(batch);
            return new BatchExecutionContext(batch.getTemplateId(), batch.getColumnMapping());
        });

        if (ctx == null) {
            return;
        }

        List<CertificateBatchItemEntity> allItems = itemRepository.findByBatchIdOrderByRowNumberAsc(batchId);
        int succeededCount = 0;
        int failedCount = 0;
        int invalidCount = 0;
        List<UUID> succeededCertificateIds = new ArrayList<>();

        for (CertificateBatchItemEntity item : allItems) {
            if (item.getStatus() == BatchItemStatus.SUCCEEDED && item.getCertificateId() != null) {
                succeededCount++;
                succeededCertificateIds.add(item.getCertificateId());
            } else if (item.getStatus() == BatchItemStatus.INVALID) {
                invalidCount++;
            } else if (item.getStatus() == BatchItemStatus.FAILED) {
                failedCount++;
            }
        }

        for (CertificateBatchItemEntity item : allItems) {
            if (item.getStatus() != BatchItemStatus.VALID) {
                continue;
            }

            UUID itemId = item.getId();
            transactionTemplate.executeWithoutResult(
                    status -> itemRepository.findById(itemId).ifPresent(entity -> {
                        entity.markProcessing();
                        itemRepository.save(entity);
                    }));

            try {
                BatchRowMapperAndValidator.RowOutcome validated = rowValidator.validateAndBuildIssueRequest(
                        ctx.templateId(), ctx.columnMapping(), item.getInputData(), item.getIdempotencyKey());
                if (!validated.valid()) {
                    throw new IllegalArgumentException(validated.errorMessage());
                }

                IssueCertificateRequest issueRequest = validated.issueRequest();
                CertificateService.IssueOutcome outcome =
                        certificateService.issueCertificate(organizationId, actorUserId, issueRequest);
                UUID issuedCertId = outcome.certificate().id();

                succeededCount++;
                succeededCertificateIds.add(issuedCertId);

                int finalSucceeded = succeededCount;
                int finalFailed = failedCount;
                transactionTemplate.executeWithoutResult(status -> {
                    itemRepository.findById(itemId).ifPresent(entity -> {
                        entity.markSucceeded(issuedCertId);
                        itemRepository.save(entity);
                    });
                    batchRepository
                            .findByIdAndOrganizationId(batchId, organizationId)
                            .ifPresent(batch -> {
                                batch.updateProgress(finalSucceeded, finalFailed);
                                batchRepository.save(batch);
                            });
                });
            } catch (Exception ex) {
                failedCount++;
                String errorMsg = truncateError(ex.getMessage());

                int finalSucceeded = succeededCount;
                int finalFailed = failedCount;
                transactionTemplate.executeWithoutResult(status -> {
                    itemRepository.findById(itemId).ifPresent(entity -> {
                        entity.markFailed(errorMsg);
                        itemRepository.save(entity);
                    });
                    batchRepository
                            .findByIdAndOrganizationId(batchId, organizationId)
                            .ifPresent(batch -> {
                                batch.updateProgress(finalSucceeded, finalFailed);
                                batchRepository.save(batch);
                            });
                });
            }
        }

        List<CertificateBatchItemEntity> refreshedItems = itemRepository.findByBatchIdOrderByRowNumberAsc(batchId);
        String zipObjectKey = buildAndStoreZipIfAny(batchId, organizationId, refreshedItems);

        BatchStatus terminalStatus;
        if (succeededCertificateIds.isEmpty()) {
            terminalStatus = BatchStatus.FAILED;
        } else if (failedCount > 0 || invalidCount > 0) {
            terminalStatus = BatchStatus.COMPLETED_WITH_ERRORS;
        } else {
            terminalStatus = BatchStatus.COMPLETED;
        }

        int finalSucceededTotal = succeededCount;
        int finalFailedTotal = failedCount;
        String summaryMsg =
                terminalStatus == BatchStatus.FAILED ? "No certificates could be generated for this batch." : null;

        transactionTemplate.executeWithoutResult(status -> {
            batchRepository.findByIdAndOrganizationId(batchId, organizationId).ifPresent(batch -> {
                batch.markFinished(terminalStatus, finalSucceededTotal, finalFailedTotal, zipObjectKey, summaryMsg);
                batchRepository.save(batch);
            });
            auditEventService.recordEvent(
                    organizationId,
                    actorUserId,
                    "BATCH_COMPLETED",
                    summaryMsg,
                    Map.of(
                            "batchId",
                            batchId.toString(),
                            "status",
                            terminalStatus.name(),
                            "successfulRecords",
                            finalSucceededTotal,
                            "failedRecords",
                            finalFailedTotal));
        });
    }

    private String buildAndStoreZipIfAny(UUID batchId, UUID organizationId, List<CertificateBatchItemEntity> items) {
        List<CertificateBatchItemEntity> succeededItems = items.stream()
                .filter(i -> i.getStatus() == BatchItemStatus.SUCCEEDED && i.getCertificateId() != null)
                .toList();
        if (succeededItems.isEmpty()) {
            return null;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StringBuilder manifest = new StringBuilder("rowNumber,status,certificateNumber,recipientName,errorMessage\n");
        Set<String> usedEntryNames = new LinkedHashSet<>();

        try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (CertificateBatchItemEntity item : items) {
                String certNumber = "";
                String recipientName = item.getInputData().getOrDefault("recipientName", "");

                if (item.getStatus() == BatchItemStatus.SUCCEEDED && item.getCertificateId() != null) {
                    var pdfOpt = certificateService.loadCertificatePdfForBatch(organizationId, item.getCertificateId());
                    if (pdfOpt.isPresent()) {
                        var payload = pdfOpt.get();
                        certNumber = payload.certificateNumber();
                        recipientName = payload.recipientName();

                        String entryName = uniqueZipEntryName(certNumber, recipientName, usedEntryNames);
                        zip.putNextEntry(new ZipEntry(entryName));
                        zip.write(payload.pdfBytes());
                        zip.closeEntry();
                    }
                }

                manifest.append(item.getRowNumber())
                        .append(',')
                        .append(csvEscape(item.getStatus().name()))
                        .append(',')
                        .append(csvEscape(certNumber))
                        .append(',')
                        .append(csvEscape(recipientName))
                        .append(',')
                        .append(csvEscape(item.getErrorMessage() == null ? "" : item.getErrorMessage()))
                        .append('\n');
            }

            zip.putNextEntry(new ZipEntry("manifest.csv"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to assemble certificate batch ZIP archive", ex);
        }

        String zipObjectKey = "orgs/" + organizationId + "/batches/" + batchId + "/certificates.zip";
        storageService.store(zipObjectKey, baos.toByteArray());
        return zipObjectKey;
    }

    private static String uniqueZipEntryName(String certNumber, String recipientName, Set<String> usedNames) {
        String safeRecipient = (recipientName == null ? "recipient" : recipientName)
                .replaceAll("[^a-zA-Z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (safeRecipient.isEmpty()) {
            safeRecipient = "recipient";
        }
        if (safeRecipient.length() > 40) {
            safeRecipient = safeRecipient.substring(0, 40);
        }
        String baseName = certNumber + "_" + safeRecipient;
        String candidate = baseName + ".pdf";
        int suffix = 2;
        while (!usedNames.add(candidate)) {
            candidate = baseName + "_" + suffix + ".pdf";
            suffix++;
        }
        return candidate;
    }

    private static String csvEscape(String value) {
        String raw = value == null ? "" : value;
        if (!raw.isEmpty()
                && (raw.charAt(0) == '='
                        || raw.charAt(0) == '+'
                        || raw.charAt(0) == '-'
                        || raw.charAt(0) == '@'
                        || raw.charAt(0) == '\t'
                        || raw.charAt(0) == '\r')) {
            raw = "'" + raw;
        }
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    private static String truncateError(String message) {
        if (message == null || message.isBlank()) {
            return "Certificate generation failed for this row.";
        }
        String trimmed = message.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private record BatchExecutionContext(UUID templateId, Map<String, String> columnMapping) {}
}
