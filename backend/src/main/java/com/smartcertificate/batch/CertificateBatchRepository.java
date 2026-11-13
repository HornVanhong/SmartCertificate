package com.smartcertificate.batch;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CertificateBatchRepository extends JpaRepository<CertificateBatchEntity, UUID> {

    Optional<CertificateBatchEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<CertificateBatchEntity> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);

    Page<CertificateBatchEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<CertificateBatchEntity> findByOrganizationIdAndCreatedByOrderByCreatedAtDesc(
            UUID organizationId, UUID createdBy, Pageable pageable);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndCreatedBy(UUID organizationId, UUID createdBy);

    long countByOrganizationIdAndStatusIn(UUID organizationId, Collection<BatchStatus> statuses);

    long countByOrganizationIdAndCreatedByAndStatusIn(
            UUID organizationId, UUID createdBy, Collection<BatchStatus> statuses);
}
