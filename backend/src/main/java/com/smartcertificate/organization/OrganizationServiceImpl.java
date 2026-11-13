package com.smartcertificate.organization;

import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.media.MediaAssetService;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrganizationServiceImpl implements OrganizationService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final OrganizationRepository organizationRepository;
    private final MediaAssetService mediaAssetService;
    private final AuditEventService auditEventService;

    OrganizationServiceImpl(
            OrganizationRepository organizationRepository,
            MediaAssetService mediaAssetService,
            AuditEventService auditEventService) {
        this.organizationRepository = organizationRepository;
        this.mediaAssetService = mediaAssetService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getCurrentOrganization(UUID organizationId) {
        OrganizationEntity entity = requireEntity(organizationId);
        return toResponse(entity);
    }

    @Override
    @Transactional
    public OrganizationResponse updateCurrentOrganization(
            UUID organizationId, UUID actorUserId, UpdateOrganizationRequest request) {
        OrganizationEntity entity = requireEntity(organizationId);
        if (request.logoAssetId() != null) {
            mediaAssetService.assertAssetBelongsToOrganization(request.logoAssetId(), organizationId);
        }
        Map<String, Object> sanitizedBrand = sanitizeBrandSettings(request.brandSettings());

        entity.updateProfile(
                request.name().trim(),
                request.logoAssetId(),
                trimToNull(request.contactEmail()),
                trimToNull(request.phone()),
                trimToNull(request.address()),
                trimToNull(request.website()),
                sanitizedBrand);

        OrganizationEntity saved = organizationRepository.save(entity);
        auditEventService.recordEvent(
                organizationId, actorUserId, "ORG_PROFILE_UPDATED", null, Map.of("organizationName", saved.getName()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public OrganizationSummary createOrganization(String name, String contactEmail) {
        String trimmedName = name.trim();
        String baseSlug = toSlug(trimmedName);
        String slug = ensureUniqueSlug(baseSlug);
        OrganizationEntity saved =
                organizationRepository.save(new OrganizationEntity(trimmedName, slug, trimToNull(contactEmail)));
        return new OrganizationSummary(saved.getId(), saved.getName(), saved.getSlug());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationSummary getSummary(UUID organizationId) {
        OrganizationEntity entity = requireEntity(organizationId);
        return new OrganizationSummary(entity.getId(), entity.getName(), entity.getSlug());
    }

    private OrganizationEntity requireEntity(UUID organizationId) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Organization was not found."));
    }

    private OrganizationResponse toResponse(OrganizationEntity entity) {
        String logoUrl = entity.getLogoAssetId() != null
                ? mediaAssetService.buildSignedContentUrl(entity.getLogoAssetId(), entity.getId())
                : null;
        return new OrganizationResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getLogoAssetId(),
                logoUrl,
                entity.getContactEmail(),
                entity.getPhone(),
                entity.getAddress(),
                entity.getWebsite(),
                entity.getBrandSettings(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private Map<String, Object> sanitizeBrandSettings(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of("primaryColor", "#142D4E", "accentColor", "#C6A45B");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        validateColor(raw, "primaryColor", result);
        validateColor(raw, "accentColor", result);
        if (raw.get("defaultIssuerTitle") instanceof String title && !title.isBlank()) {
            result.put(
                    "defaultIssuerTitle",
                    title.trim().substring(0, Math.min(title.trim().length(), 120)));
        }
        return result;
    }

    private void validateColor(Map<String, Object> raw, String key, Map<String, Object> target) {
        Object value = raw.get(key);
        if (value == null) {
            return;
        }
        if (!(value instanceof String str) || !HEX_COLOR.matcher(str.trim()).matches()) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Brand color must be a valid 6-digit hex color.",
                    Map.of("brandSettings." + key, "Must be a valid hex color such as #142D4E."));
        }
        target.put(key, str.trim().toUpperCase(Locale.ROOT));
    }

    private String ensureUniqueSlug(String baseSlug) {
        String candidate = baseSlug;
        int attempt = 1;
        while (organizationRepository.existsBySlug(candidate)) {
            String suffix = "-" + (++attempt);
            int maxPrefix = Math.max(1, 120 - suffix.length());
            candidate = baseSlug.substring(0, Math.min(baseSlug.length(), maxPrefix)) + suffix;
        }
        return candidate;
    }

    private static String toSlug(String name) {
        String slug =
                name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            return "org-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return slug.substring(0, Math.min(slug.length(), 100));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
