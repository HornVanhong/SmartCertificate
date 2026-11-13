package com.smartcertificate.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TemplateVersionRepository extends JpaRepository<TemplateVersionEntity, UUID> {

    Optional<TemplateVersionEntity> findByTemplateIdAndVersionNumber(UUID templateId, int versionNumber);

    List<TemplateVersionEntity> findByTemplateIdOrderByVersionNumberDesc(UUID templateId);

    List<TemplateVersionEntity> findByTemplateIdIn(List<UUID> templateIds);
}
