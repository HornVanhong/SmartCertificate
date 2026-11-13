package com.smartcertificate.certificate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CertificateRepository
        extends JpaRepository<CertificateEntity, UUID>, JpaSpecificationExecutor<CertificateEntity> {

    Optional<CertificateEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<CertificateEntity> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);

    Optional<CertificateEntity> findByVerificationTokenHash(String verificationTokenHash);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndCreatedBy(UUID organizationId, UUID createdBy);

    long countByOrganizationIdAndStatus(UUID organizationId, CertificateStatus status);

    long countByOrganizationIdAndCreatedByAndStatus(UUID organizationId, UUID createdBy, CertificateStatus status);

    long countByOrganizationIdAndIssuedAtAfter(UUID organizationId, Instant since);

    long countByOrganizationIdAndCreatedByAndIssuedAtAfter(UUID organizationId, UUID createdBy, Instant since);

    @Query("SELECT c.issuedAt FROM CertificateEntity c WHERE c.organizationId = :orgId AND c.issuedAt >= :since")
    List<Instant> findIssuedTimestampsSince(@Param("orgId") UUID organizationId, @Param("since") Instant since);

    @Query(
            "SELECT c.issuedAt FROM CertificateEntity c WHERE c.organizationId = :orgId AND c.createdBy = :createdBy AND c.issuedAt >= :since")
    List<Instant> findCreatorIssuedTimestampsSince(
            @Param("orgId") UUID organizationId, @Param("createdBy") UUID createdBy, @Param("since") Instant since);

    @Query(value = "SELECT nextval('certificate_number_seq')", nativeQuery = true)
    long nextCertificateSequence();
}
