package com.smartcertificate.template;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TemplateService {

    record PublishedTemplateSnapshot(
            UUID templateId,
            UUID templateVersionId,
            int versionNumber,
            String templateName,
            DesignDocument designJson) {}

    record TemplateStats(long totalTemplates, long publishedTemplates, long draftTemplates) {}

    Page<TemplateSummaryResponse> listTemplates(
            UUID organizationId,
            String query,
            TemplateStatus status,
            String category,
            TemplateOrientation orientation,
            Pageable pageable);

    TemplateDetailResponse createDraft(UUID organizationId, UUID actorUserId, CreateTemplateRequest request);

    TemplateDetailResponse getTemplate(UUID organizationId, UUID templateId);

    TemplateVersionResponse getTemplateVersion(UUID organizationId, UUID templateId, int versionNumber);

    TemplateDetailResponse updateDraft(
            UUID organizationId, UUID actorUserId, UUID templateId, UpdateTemplateDraftRequest request);

    TemplateDetailResponse duplicateTemplate(
            UUID organizationId, UUID actorUserId, UUID templateId, DuplicateTemplateRequest request);

    TemplatePreviewResponse previewTemplate(UUID organizationId, UUID templateId, PreviewTemplateRequest request);

    TemplateDetailResponse publishTemplate(UUID organizationId, UUID actorUserId, UUID templateId);

    TemplateDetailResponse archiveTemplate(UUID organizationId, UUID actorUserId, UUID templateId);

    PublishedTemplateSnapshot getPublishedTemplateForIssuance(UUID organizationId, UUID templateId);

    TemplateStats getTemplateStats(UUID organizationId);
}
