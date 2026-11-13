package com.smartcertificate.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignDocument(String schemaVersion, DesignPage page, List<DesignElement> elements) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DesignPage(
            double width,
            double height,
            String unit,
            TemplateOrientation orientation,
            String backgroundColor,
            UUID backgroundAssetId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DesignElement(
            String id,
            DesignElementType type,
            String name,
            double x,
            double y,
            double width,
            double height,
            double rotation,
            double opacity,
            boolean visible,
            boolean locked,
            int zIndex,
            // TEXT properties
            String text,
            String fontFamily,
            Double fontSize,
            String fontWeight,
            String fontStyle,
            String fill,
            String textAlign,
            Double lineHeight,
            // IMAGE / LOGO / SIGNATURE / STAMP properties
            UUID assetId,
            String fit,
            // SHAPE properties
            String shapeKind,
            String stroke,
            Double strokeWidth,
            Double cornerRadius,
            // QR properties
            String qrDataTemplate,
            String foregroundColor,
            String backgroundColor) {}
}
