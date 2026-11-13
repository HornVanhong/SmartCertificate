package com.smartcertificate.template;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TemplateSummaryResponse(
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
        DesignDocument previewDesign,
        Map<UUID, String> assetUrls,
        Instant createdAt,
        Instant updatedAt) {}
