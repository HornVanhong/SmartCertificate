package com.smartcertificate.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID>, JpaSpecificationExecutor<AuditLogEntity> {

    Page<AuditLogEntity> findByOrganizationIdAndCertificateIdOrderByCreatedAtDesc(
            UUID organizationId, UUID certificateId, Pageable pageable);
}
