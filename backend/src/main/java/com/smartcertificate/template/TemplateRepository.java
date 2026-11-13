package com.smartcertificate.template;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TemplateRepository extends JpaRepository<TemplateEntity, UUID>, JpaSpecificationExecutor<TemplateEntity> {

    Optional<TemplateEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, TemplateStatus status);
}
