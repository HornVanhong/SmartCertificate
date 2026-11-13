package com.smartcertificate.template;

import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.media.MediaAssetService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Validates canonical versioned DesignDocument payloads against geometry, styling, and tenant asset rules. */
@Service
class DesignSchemaValidator {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";
    private static final int MAX_ELEMENTS = 200;
    private static final double MIN_PAGE_DIMENSION = 100.0;
    private static final double MAX_PAGE_DIMENSION = 5000.0;
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("\\{\\{([^}]*)\\}\\}");
    private static final Pattern VALID_PLACEHOLDER_KEY = Pattern.compile("^[a-zA-Z0-9_]{1,64}$");
    private static final Set<String> ALLOWED_FONTS =
            Set.of("Inter", "Noto Sans Khmer", "Georgia", "Times New Roman", "Courier New");
    private static final Set<String> ALLOWED_WEIGHTS = Set.of("NORMAL", "BOLD");
    private static final Set<String> ALLOWED_STYLES = Set.of("NORMAL", "ITALIC");
    private static final Set<String> ALLOWED_ALIGNS = Set.of("LEFT", "CENTER", "RIGHT");
    private static final Set<String> ALLOWED_SHAPES = Set.of("RECTANGLE", "CIRCLE", "LINE");

    private final MediaAssetService mediaAssetService;

    DesignSchemaValidator(MediaAssetService mediaAssetService) {
        this.mediaAssetService = mediaAssetService;
    }

    List<String> validate(
            DesignDocument doc,
            UUID organizationId,
            TemplateOrientation expectedOrientation,
            double expectedWidth,
            double expectedHeight) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        if (doc == null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Design JSON is required.",
                    Map.of("designJson", "Design JSON must not be null."));
        }

        if (!SUPPORTED_SCHEMA_VERSION.equals(doc.schemaVersion())) {
            fieldErrors.put(
                    "designJson.schemaVersion",
                    "Unsupported schemaVersion. Expected '" + SUPPORTED_SCHEMA_VERSION + "'.");
        }

        validatePage(doc.page(), organizationId, expectedOrientation, expectedWidth, expectedHeight, fieldErrors);
        List<String> warnings = validateElements(doc.elements(), organizationId, fieldErrors);

        if (!fieldErrors.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Template design JSON failed validation.", fieldErrors);
        }
        return warnings;
    }

    private void validatePage(
            DesignDocument.DesignPage page,
            UUID organizationId,
            TemplateOrientation expectedOrientation,
            double expectedWidth,
            double expectedHeight,
            Map<String, String> fieldErrors) {
        if (page == null) {
            fieldErrors.put("designJson.page", "Page configuration is required.");
            return;
        }
        if (page.width() < MIN_PAGE_DIMENSION || page.width() > MAX_PAGE_DIMENSION) {
            fieldErrors.put("designJson.page.width", "Page width must be between 100 and 5000.");
        }
        if (page.height() < MIN_PAGE_DIMENSION || page.height() > MAX_PAGE_DIMENSION) {
            fieldErrors.put("designJson.page.height", "Page height must be between 100 and 5000.");
        }
        if (Math.abs(page.width() - expectedWidth) > 0.5 || Math.abs(page.height() - expectedHeight) > 0.5) {
            fieldErrors.put(
                    "designJson.page", "Page dimensions in designJson must match template pageWidth and pageHeight.");
        }
        if (!"pt".equals(page.unit()) && !"px".equals(page.unit())) {
            fieldErrors.put("designJson.page.unit", "Page unit must be 'pt' or 'px'.");
        }
        if (page.orientation() == null || page.orientation() != expectedOrientation) {
            fieldErrors.put(
                    "designJson.page.orientation", "Page orientation in designJson must match template orientation.");
        }
        if (page.backgroundColor() == null
                || !HEX_COLOR.matcher(page.backgroundColor()).matches()) {
            fieldErrors.put("designJson.page.backgroundColor", "Background color must be a valid 6-digit hex color.");
        }
        if (page.backgroundAssetId() != null) {
            mediaAssetService.assertAssetBelongsToOrganization(page.backgroundAssetId(), organizationId);
        }
    }

    private List<String> validateElements(
            List<DesignDocument.DesignElement> elements, UUID organizationId, Map<String, String> fieldErrors) {
        List<String> warnings = new ArrayList<>();
        if (elements == null) {
            fieldErrors.put("designJson.elements", "Elements list is required.");
            return warnings;
        }
        if (elements.size() > MAX_ELEMENTS) {
            fieldErrors.put(
                    "designJson.elements", "Template must not contain more than " + MAX_ELEMENTS + " elements.");
            return warnings;
        }

        Set<String> seenIds = new HashSet<>();
        boolean hasQr = false;

        for (int i = 0; i < elements.size(); i++) {
            DesignDocument.DesignElement el = elements.get(i);
            String prefix = "designJson.elements[" + i + "]";

            if (el == null) {
                fieldErrors.put(prefix, "Element must not be null.");
                continue;
            }
            if (el.id() == null || el.id().isBlank() || el.id().length() > 80) {
                fieldErrors.put(prefix + ".id", "Element id must be between 1 and 80 characters.");
            } else if (!seenIds.add(el.id())) {
                fieldErrors.put(prefix + ".id", "Duplicate element id: " + el.id());
            }
            if (el.type() == null) {
                fieldErrors.put(prefix + ".type", "Element type is required.");
                continue;
            }
            if (el.width() <= 0 || el.height() <= 0) {
                fieldErrors.put(prefix + ".dimensions", "Element width and height must be positive.");
            }
            if (el.opacity() < 0.0 || el.opacity() > 1.0) {
                fieldErrors.put(prefix + ".opacity", "Opacity must be between 0 and 1.");
            }
            if (el.rotation() < -360.0 || el.rotation() > 360.0) {
                fieldErrors.put(prefix + ".rotation", "Rotation must be between -360 and 360 degrees.");
            }

            switch (el.type()) {
                case TEXT -> validateTextElement(el, prefix, fieldErrors);
                case IMAGE, LOGO, SIGNATURE, STAMP -> {
                    if (el.assetId() != null) {
                        mediaAssetService.assertAssetBelongsToOrganization(el.assetId(), organizationId);
                    } else if (el.type() == DesignElementType.IMAGE) {
                        fieldErrors.put(prefix + ".assetId", "IMAGE element requires an organization assetId.");
                    }
                }
                case SHAPE -> validateShapeElement(el, prefix, fieldErrors);
                case QR -> {
                    hasQr = true;
                    validateQrElement(el, prefix, fieldErrors, warnings);
                }
            }
        }

        if (!hasQr) {
            warnings.add("Template does not include a QR verification placeholder element.");
        }
        return warnings;
    }

    private void validateTextElement(DesignDocument.DesignElement el, String prefix, Map<String, String> fieldErrors) {
        if (el.text() == null) {
            fieldErrors.put(prefix + ".text", "TEXT element requires text content.");
        } else {
            validatePlaceholders(el.text(), prefix + ".text", fieldErrors);
        }
        if (el.fontFamily() == null || !ALLOWED_FONTS.contains(el.fontFamily())) {
            fieldErrors.put(prefix + ".fontFamily", "Unsupported fontFamily.");
        }
        if (el.fontSize() == null || el.fontSize() < 6.0 || el.fontSize() > 200.0) {
            fieldErrors.put(prefix + ".fontSize", "fontSize must be between 6 and 200.");
        }
        if (el.fontWeight() != null && !ALLOWED_WEIGHTS.contains(el.fontWeight())) {
            fieldErrors.put(prefix + ".fontWeight", "fontWeight must be NORMAL or BOLD.");
        }
        if (el.fontStyle() != null && !ALLOWED_STYLES.contains(el.fontStyle())) {
            fieldErrors.put(prefix + ".fontStyle", "fontStyle must be NORMAL or ITALIC.");
        }
        if (el.fill() == null || !HEX_COLOR.matcher(el.fill()).matches()) {
            fieldErrors.put(prefix + ".fill", "Text fill must be a valid 6-digit hex color.");
        }
        if (el.textAlign() != null && !ALLOWED_ALIGNS.contains(el.textAlign())) {
            fieldErrors.put(prefix + ".textAlign", "textAlign must be LEFT, CENTER, or RIGHT.");
        }
        if (el.lineHeight() != null && (el.lineHeight() < 0.8 || el.lineHeight() > 3.0)) {
            fieldErrors.put(prefix + ".lineHeight", "lineHeight must be between 0.8 and 3.0.");
        }
    }

    private void validateShapeElement(DesignDocument.DesignElement el, String prefix, Map<String, String> fieldErrors) {
        if (el.shapeKind() == null || !ALLOWED_SHAPES.contains(el.shapeKind())) {
            fieldErrors.put(prefix + ".shapeKind", "shapeKind must be RECTANGLE, CIRCLE, or LINE.");
        }
        if (el.fill() != null
                && !"transparent".equalsIgnoreCase(el.fill())
                && !HEX_COLOR.matcher(el.fill()).matches()) {
            fieldErrors.put(prefix + ".fill", "Shape fill must be a 6-digit hex color or 'transparent'.");
        }
        if (el.stroke() != null && !HEX_COLOR.matcher(el.stroke()).matches()) {
            fieldErrors.put(prefix + ".stroke", "Shape stroke must be a 6-digit hex color.");
        }
        if (el.strokeWidth() != null && (el.strokeWidth() < 0.0 || el.strokeWidth() > 40.0)) {
            fieldErrors.put(prefix + ".strokeWidth", "strokeWidth must be between 0 and 40.");
        }
    }

    private void validateQrElement(
            DesignDocument.DesignElement el, String prefix, Map<String, String> fieldErrors, List<String> warnings) {
        if (el.qrDataTemplate() == null || el.qrDataTemplate().isBlank()) {
            fieldErrors.put(prefix + ".qrDataTemplate", "QR element requires qrDataTemplate.");
        } else {
            validatePlaceholders(el.qrDataTemplate(), prefix + ".qrDataTemplate", fieldErrors);
        }
        if (el.foregroundColor() != null
                && !HEX_COLOR.matcher(el.foregroundColor()).matches()) {
            fieldErrors.put(prefix + ".foregroundColor", "QR foregroundColor must be a 6-digit hex color.");
        }
        if (el.backgroundColor() != null
                && !HEX_COLOR.matcher(el.backgroundColor()).matches()) {
            fieldErrors.put(prefix + ".backgroundColor", "QR backgroundColor must be a 6-digit hex color.");
        }
        if (el.width() < 48.0 || el.height() < 48.0) {
            warnings.add("QR element '" + el.id() + "' is smaller than 48pt and may be difficult to scan.");
        }
    }

    private void validatePlaceholders(String text, String fieldKey, Map<String, String> fieldErrors) {
        Matcher matcher = PLACEHOLDER_TOKEN.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!VALID_PLACEHOLDER_KEY.matcher(key).matches()) {
                fieldErrors.put(
                        fieldKey,
                        "Invalid placeholder syntax '{{" + key + "}}'. Use alphanumeric/underscore keys only.");
                return;
            }
        }
    }
}
