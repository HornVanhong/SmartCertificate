package com.smartcertificate.template;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TemplatePreviewResponse(
        UUID templateId,
        int versionNumber,
        BigDecimal pageWidth,
        BigDecimal pageHeight,
        TemplateOrientation orientation,
        DesignDocument resolvedDesignJson,
        Map<String, String> resolvedPlaceholders,
        Map<UUID, String> assetUrls,
        List<String> warnings) {}
