package com.smartcertificate.template;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TemplateDetailResponse(
        UUID id,
        String name,
        String description,
        String category,
        TemplateOrientation orientation,
        BigDecimal pageWidth,
        BigDecimal pageHeight,
        TemplateStatus status,
        int currentVersion,
        Integer publishedVersion,
        DesignDocument designJson,
        Map<UUID, String> assetUrls,
        List<TemplateVersionSummary> versions,
        Instant createdAt,
        Instant updatedAt) {

    public record TemplateVersionSummary(UUID id, int versionNumber, UUID createdBy, Instant createdAt) {}
}
