package com.smartcertificate.batch;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface CertificateBatchService {

    record ImportOutcome(BatchDetailResponse batch, boolean idempotentReplay) {}

    record ZipDownloadPayload(String fileName, String contentType, byte[] bytes) {}

    record BatchStats(long totalBatches, long activeBatches) {}

    Page<BatchSummaryResponse> listBatches(
            UUID organizationId, UUID actorUserId, boolean canReadAll, Pageable pageable);

    ImportOutcome importBatch(
            UUID organizationId, UUID actorUserId, UUID templateId, String idempotencyKey, MultipartFile file);

    BatchDetailResponse getBatch(UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId);

    BatchDetailResponse saveMapping(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId, BatchColumnMappingRequest request);

    BatchDetailResponse validateBatch(UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId);

    BatchDetailResponse generateBatch(UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId);

    BatchDetailResponse retryFailedItems(UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId);

    Page<BatchErrorItemResponse> listBatchErrors(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId, Pageable pageable);

    ZipDownloadPayload downloadBatchZip(UUID organizationId, UUID actorUserId, boolean canReadAll, UUID batchId);

    BatchStats getBatchStats(UUID organizationId, UUID actorUserId, boolean canReadAll);
}
