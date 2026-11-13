package com.smartcertificate.batch;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CertificateBatchItemRepository extends JpaRepository<CertificateBatchItemEntity, UUID> {

    List<CertificateBatchItemEntity> findByBatchIdOrderByRowNumberAsc(UUID batchId);

    Page<CertificateBatchItemEntity> findByBatchIdOrderByRowNumberAsc(UUID batchId, Pageable pageable);

    List<CertificateBatchItemEntity> findByBatchIdAndStatusInOrderByRowNumberAsc(
            UUID batchId, Collection<BatchItemStatus> statuses);

    Page<CertificateBatchItemEntity> findByBatchIdAndStatusInOrderByRowNumberAsc(
            UUID batchId, Collection<BatchItemStatus> statuses, Pageable pageable);

    long countByBatchIdAndStatus(UUID batchId, BatchItemStatus status);
}
