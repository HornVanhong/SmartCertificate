package com.smartcertificate.template;

import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.media.MediaAssetService;
import com.smartcertificate.organization.OrganizationService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TemplateServiceImpl implements TemplateService {

    private static final BigDecimal A4_LANDSCAPE_WIDTH = new BigDecimal("842.00");
    private static final BigDecimal A4_LANDSCAPE_HEIGHT = new BigDecimal("595.00");
    private static final BigDecimal A4_PORTRAIT_WIDTH = new BigDecimal("595.00");
    private static final BigDecimal A4_PORTRAIT_HEIGHT = new BigDecimal("842.00");
    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final DesignSchemaValidator designSchemaValidator;
    private final StarterDesignFactory starterDesignFactory;
    private final OrganizationService organizationService;
    private final MediaAssetService mediaAssetService;
    private final AuditEventService auditEventService;

    TemplateServiceImpl(
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            DesignSchemaValidator designSchemaValidator,
            StarterDesignFactory starterDesignFactory,
            OrganizationService organizationService,
            MediaAssetService mediaAssetService,
            AuditEventService auditEventService) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.designSchemaValidator = designSchemaValidator;
        this.starterDesignFactory = starterDesignFactory;
        this.organizationService = organizationService;
        this.mediaAssetService = mediaAssetService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TemplateSummaryResponse> listTemplates(
            UUID organizationId,
            String query,
            TemplateStatus status,
            String category,
            TemplateOrientation orientation,
            Pageable pageable) {
        String normalizedQuery = trimToNull(query);
        String normalizedCategory = trimToNull(category);

        Specification<TemplateEntity> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (orientation != null) {
                predicates.add(cb.equal(root.get("orientation"), orientation));
            }
            if (normalizedCategory != null) {
                predicates.add(cb.equal(cb.lower(root.get("category")), normalizedCategory.toLowerCase(Locale.ROOT)));
            }
            if (normalizedQuery != null) {
                String pattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<TemplateEntity> page = templateRepository.findAll(spec, sortedPageable);

        List<UUID> templateIds =
                page.getContent().stream().map(TemplateEntity::getId).toList();
        Map<String, DesignDocument> versionsByKey = new HashMap<>();
        if (!templateIds.isEmpty()) {
            for (TemplateVersionEntity v : templateVersionRepository.findByTemplateIdIn(templateIds)) {
                versionsByKey.put(v.getTemplateId() + ":" + v.getVersionNumber(), v.getDesignJson());
            }
        }

        return page.map(template -> {
            DesignDocument preview = versionsByKey.get(template.getId() + ":" + template.getCurrentVersion());
            return new TemplateSummaryResponse(
                    template.getId(),
                    template.getName(),
                    template.getDescription(),
                    template.getCategory(),
                    template.getOrientation(),
                    template.getPageWidth(),
                    template.getPageHeight(),
                    template.getStatus(),
                    template.getCurrentVersion(),
                    template.getPublishedVersion(),
                    preview,
                    collectAssetUrls(preview, template.getOrganizationId()),
                    template.getCreatedAt(),
                    template.getUpdatedAt());
        });
    }

    @Override
    @Transactional
    public TemplateDetailResponse createDraft(UUID organizationId, UUID actorUserId, CreateTemplateRequest request) {
        BigDecimal width = resolveWidth(request.orientation(), request.pageWidth());
        BigDecimal height = resolveHeight(request.orientation(), request.pageHeight());

        OrganizationService.OrganizationSummary org = organizationService.getSummary(organizationId);
        DesignDocument design = request.designJson() != null
                ? request.designJson()
                : starterDesignFactory.createStarterDesign(
                        request.orientation(), width.doubleValue(), height.doubleValue(), org.name());

        designSchemaValidator.validate(
                design, organizationId, request.orientation(), width.doubleValue(), height.doubleValue());

        TemplateEntity template = templateRepository.save(new TemplateEntity(
                organizationId,
                request.name().trim(),
                trimToNull(request.description()),
                trimToNull(request.category()),
                request.orientation(),
                width,
                height,
                actorUserId));

        templateVersionRepository.save(new TemplateVersionEntity(template.getId(), 1, design, actorUserId));

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "TEMPLATE_CREATED",
                null,
                Map.of("templateId", template.getId().toString(), "name", template.getName()));

        return buildDetailResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateDetailResponse getTemplate(UUID organizationId, UUID templateId) {
        TemplateEntity template = requireTemplate(organizationId, templateId);
        return buildDetailResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateVersionResponse getTemplateVersion(UUID organizationId, UUID templateId, int versionNumber) {
        requireTemplate(organizationId, templateId);
        TemplateVersionEntity version = templateVersionRepository
                .findByTemplateIdAndVersionNumber(templateId, versionNumber)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Template version was not found."));
        return new TemplateVersionResponse(
                version.getId(),
                version.getVersionNumber(),
                version.getDesignJson(),
                version.getCreatedBy(),
                version.getCreatedAt());
    }

    @Override
    @Transactional
    public TemplateDetailResponse updateDraft(
            UUID organizationId, UUID actorUserId, UUID templateId, UpdateTemplateDraftRequest request) {
        TemplateEntity template = requireTemplate(organizationId, templateId);
        if (template.getStatus() == TemplateStatus.ARCHIVED) {
            throw new AppException(
                    ErrorCode.CONFLICT,
                    "Archived templates cannot be edited. Duplicate the template to create a new draft.");
        }

        designSchemaValidator.validate(
                request.designJson(),
                organizationId,
                request.orientation(),
                request.pageWidth().doubleValue(),
                request.pageHeight().doubleValue());

        int targetVersion;
        if (template.getStatus() == TemplateStatus.PUBLISHED) {
            // Published versions remain immutable; editing creates a new draft version.
            targetVersion = template.getCurrentVersion() + 1;
            templateVersionRepository.save(
                    new TemplateVersionEntity(template.getId(), targetVersion, request.designJson(), actorUserId));
        } else {
            targetVersion = template.getCurrentVersion();
            TemplateVersionEntity current = templateVersionRepository
                    .findByTemplateIdAndVersionNumber(template.getId(), targetVersion)
                    .orElseThrow(() -> new IllegalStateException("Current draft version missing"));
            current.updateDraftDesign(request.designJson());
            templateVersionRepository.save(current);
        }

        template.updateDraftMetadata(
                request.name().trim(),
                trimToNull(request.description()),
                trimToNull(request.category()),
                request.orientation(),
                request.pageWidth(),
                request.pageHeight(),
                targetVersion);
        templateRepository.save(template);

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "TEMPLATE_DRAFT_UPDATED",
                null,
                Map.of("templateId", template.getId().toString(), "versionNumber", targetVersion));

        return buildDetailResponse(template);
    }

    @Override
    @Transactional
    public TemplateDetailResponse duplicateTemplate(
            UUID organizationId, UUID actorUserId, UUID templateId, DuplicateTemplateRequest request) {
        TemplateEntity source = requireTemplate(organizationId, templateId);
        TemplateVersionEntity sourceVersion = templateVersionRepository
                .findByTemplateIdAndVersionNumber(source.getId(), source.getCurrentVersion())
                .orElseThrow(() -> new IllegalStateException("Source template version missing"));

        String copyName =
                (request != null && request.name() != null && !request.name().isBlank())
                        ? request.name().trim()
                        : buildCopyName(source.getName());

        TemplateEntity copy = templateRepository.save(new TemplateEntity(
                organizationId,
                copyName,
                source.getDescription(),
                source.getCategory(),
                source.getOrientation(),
                source.getPageWidth(),
                source.getPageHeight(),
                actorUserId));

        templateVersionRepository.save(
                new TemplateVersionEntity(copy.getId(), 1, sourceVersion.getDesignJson(), actorUserId));

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "TEMPLATE_DUPLICATED",
                null,
                Map.of(
                        "sourceTemplateId", source.getId().toString(),
                        "newTemplateId", copy.getId().toString()));

        return buildDetailResponse(copy);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplatePreviewResponse previewTemplate(
            UUID organizationId, UUID templateId, PreviewTemplateRequest request) {
        TemplateEntity template = requireTemplate(organizationId, templateId);
        TemplateVersionEntity version = templateVersionRepository
                .findByTemplateIdAndVersionNumber(template.getId(), template.getCurrentVersion())
                .orElseThrow(() -> new IllegalStateException("Template version missing"));

        DesignDocument sourceDesign =
                (request != null && request.designJson() != null) ? request.designJson() : version.getDesignJson();

        List<String> warnings = designSchemaValidator.validate(
                sourceDesign,
                organizationId,
                template.getOrientation(),
                template.getPageWidth().doubleValue(),
                template.getPageHeight().doubleValue());

        OrganizationService.OrganizationSummary org = organizationService.getSummary(organizationId);
        Map<String, String> placeholders =
                buildSamplePlaceholders(org.name(), request != null ? request.sampleData() : null);

        List<DesignDocument.DesignElement> resolvedElements = sourceDesign.elements().stream()
                .map(el -> resolveElementPlaceholders(el, placeholders))
                .toList();
        DesignDocument resolvedDoc =
                new DesignDocument(sourceDesign.schemaVersion(), sourceDesign.page(), resolvedElements);

        return new TemplatePreviewResponse(
                template.getId(),
                template.getCurrentVersion(),
                template.getPageWidth(),
                template.getPageHeight(),
                template.getOrientation(),
                resolvedDoc,
                placeholders,
                collectAssetUrls(resolvedDoc, organizationId),
                warnings);
    }

    @Override
    @Transactional
    public TemplateDetailResponse publishTemplate(UUID organizationId, UUID actorUserId, UUID templateId) {
        TemplateEntity template = requireTemplate(organizationId, templateId);
        if (template.getStatus() == TemplateStatus.ARCHIVED) {
            throw new AppException(ErrorCode.CONFLICT, "Archived templates cannot be published.");
        }

        TemplateVersionEntity current = templateVersionRepository
                .findByTemplateIdAndVersionNumber(template.getId(), template.getCurrentVersion())
                .orElseThrow(() -> new IllegalStateException("Current template version missing"));

        designSchemaValidator.validate(
                current.getDesignJson(),
                organizationId,
                template.getOrientation(),
                template.getPageWidth().doubleValue(),
                template.getPageHeight().doubleValue());

        template.markPublished();
        templateRepository.save(template);

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "TEMPLATE_PUBLISHED",
                null,
                Map.of(
                        "templateId", template.getId().toString(),
                        "versionNumber", template.getCurrentVersion()));

        return buildDetailResponse(template);
    }

    @Override
    @Transactional
    public TemplateDetailResponse archiveTemplate(UUID organizationId, UUID actorUserId, UUID templateId) {
        TemplateEntity template = requireTemplate(organizationId, templateId);
        if (template.getStatus() == TemplateStatus.ARCHIVED) {
            return buildDetailResponse(template);
        }

        template.markArchived();
        templateRepository.save(template);

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "TEMPLATE_ARCHIVED",
                null,
                Map.of("templateId", template.getId().toString()));

        return buildDetailResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedTemplateSnapshot getPublishedTemplateForIssuance(UUID organizationId, UUID templateId) {
        TemplateEntity template = requireTemplate(organizationId, templateId);
        if (template.getStatus() == TemplateStatus.ARCHIVED) {
            throw new AppException(ErrorCode.CONFLICT, "Archived templates cannot be used to issue new certificates.");
        }
        if (template.getPublishedVersion() == null) {
            throw new AppException(
                    ErrorCode.CONFLICT, "Template must have a published version before issuing certificates.");
        }

        TemplateVersionEntity version = templateVersionRepository
                .findByTemplateIdAndVersionNumber(template.getId(), template.getPublishedVersion())
                .orElseThrow(() -> new IllegalStateException("Published template version missing"));

        return new PublishedTemplateSnapshot(
                template.getId(),
                version.getId(),
                version.getVersionNumber(),
                template.getName(),
                version.getDesignJson());
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateStats getTemplateStats(UUID organizationId) {
        return new TemplateStats(
                templateRepository.countByOrganizationId(organizationId),
                templateRepository.countByOrganizationIdAndStatus(organizationId, TemplateStatus.PUBLISHED),
                templateRepository.countByOrganizationIdAndStatus(organizationId, TemplateStatus.DRAFT));
    }

    private TemplateEntity requireTemplate(UUID organizationId, UUID templateId) {
        return templateRepository
                .findByIdAndOrganizationId(templateId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Template was not found."));
    }

    private TemplateDetailResponse buildDetailResponse(TemplateEntity template) {
        List<TemplateVersionEntity> versions =
                templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(template.getId());
        DesignDocument latestDesign = versions.stream()
                .filter(v -> v.getVersionNumber() == template.getCurrentVersion())
                .findFirst()
                .map(TemplateVersionEntity::getDesignJson)
                .orElse(null);

        List<TemplateDetailResponse.TemplateVersionSummary> versionSummaries = versions.stream()
                .map(v -> new TemplateDetailResponse.TemplateVersionSummary(
                        v.getId(), v.getVersionNumber(), v.getCreatedBy(), v.getCreatedAt()))
                .toList();

        return new TemplateDetailResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getCategory(),
                template.getOrientation(),
                template.getPageWidth(),
                template.getPageHeight(),
                template.getStatus(),
                template.getCurrentVersion(),
                template.getPublishedVersion(),
                latestDesign,
                collectAssetUrls(latestDesign, template.getOrganizationId()),
                versionSummaries,
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    private Map<UUID, String> collectAssetUrls(DesignDocument doc, UUID organizationId) {
        if (doc == null) {
            return Map.of();
        }
        Map<UUID, String> urls = new LinkedHashMap<>();
        if (doc.page() != null && doc.page().backgroundAssetId() != null) {
            UUID bgId = doc.page().backgroundAssetId();
            urls.put(bgId, mediaAssetService.buildSignedContentUrl(bgId, organizationId));
        }
        if (doc.elements() != null) {
            for (DesignDocument.DesignElement el : doc.elements()) {
                if (el != null && el.assetId() != null && !urls.containsKey(el.assetId())) {
                    urls.put(el.assetId(), mediaAssetService.buildSignedContentUrl(el.assetId(), organizationId));
                }
            }
        }
        return urls;
    }

    private Map<String, String> buildSamplePlaceholders(String orgName, Map<String, String> overrides) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("recipientName", "Sokha Chan / សុខា ចាន់");
        values.put("courseName", "Full-Stack Security & Cloud Architecture");
        values.put("issuedDate", "2026-11-12");
        values.put("completionDate", "2026-11-12");
        values.put("certificateNumber", "SC-2026-000001");
        values.put("organizationName", orgName);
        values.put("instructorName", "Dr. Dara Vann");
        values.put("verificationUrl", "https://verify.smartcertificate.local/preview-sample");
        if (overrides != null) {
            overrides.forEach((k, v) -> {
                if (k != null && v != null && k.matches("^[a-zA-Z0-9_]{1,64}$")) {
                    values.put(k, v);
                }
            });
        }
        return values;
    }

    private DesignDocument.DesignElement resolveElementPlaceholders(
            DesignDocument.DesignElement el, Map<String, String> values) {
        String resolvedText = el.text() != null ? substituteTokens(el.text(), values) : null;
        String resolvedQr = el.qrDataTemplate() != null ? substituteTokens(el.qrDataTemplate(), values) : null;
        return new DesignDocument.DesignElement(
                el.id(),
                el.type(),
                el.name(),
                el.x(),
                el.y(),
                el.width(),
                el.height(),
                el.rotation(),
                el.opacity(),
                el.visible(),
                el.locked(),
                el.zIndex(),
                resolvedText,
                el.fontFamily(),
                el.fontSize(),
                el.fontWeight(),
                el.fontStyle(),
                el.fill(),
                el.textAlign(),
                el.lineHeight(),
                el.assetId(),
                el.fit(),
                el.shapeKind(),
                el.stroke(),
                el.strokeWidth(),
                el.cornerRadius(),
                resolvedQr,
                el.foregroundColor(),
                el.backgroundColor());
    }

    private String substituteTokens(String input, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER_TOKEN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.getOrDefault(key, "{{" + key + "}}");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static BigDecimal resolveWidth(TemplateOrientation orientation, BigDecimal requested) {
        if (requested != null) {
            return requested;
        }
        return orientation == TemplateOrientation.PORTRAIT ? A4_PORTRAIT_WIDTH : A4_LANDSCAPE_WIDTH;
    }

    private static BigDecimal resolveHeight(TemplateOrientation orientation, BigDecimal requested) {
        if (requested != null) {
            return requested;
        }
        return orientation == TemplateOrientation.PORTRAIT ? A4_PORTRAIT_HEIGHT : A4_LANDSCAPE_HEIGHT;
    }

    private static String buildCopyName(String originalName) {
        String suffix = " (Copy)";
        int maxBase = 180 - suffix.length();
        String base = originalName.length() > maxBase ? originalName.substring(0, maxBase) : originalName;
        return base + suffix;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
