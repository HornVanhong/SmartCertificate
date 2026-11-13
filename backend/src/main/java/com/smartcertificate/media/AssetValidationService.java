package com.smartcertificate.media;

import com.smartcertificate.common.config.StorageProperties;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Validates uploaded image assets by size, magic bytes, extension, and decoded dimensions. */
@Service
class AssetValidationService {

    private static final int MIN_DIMENSION_PX = 16;
    private static final int MAX_DIMENSION_PX = 4096;

    record ValidatedImage(
            String sanitizedFileName, String mimeType, String extension, int width, int height, byte[] bytes) {}

    private final long maxAssetSizeBytes;

    AssetValidationService(StorageProperties properties) {
        this.maxAssetSizeBytes = properties.maxAssetSizeBytes() > 0 ? properties.maxAssetSizeBytes() : 5_242_880L;
    }

    ValidatedImage validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validationError("File must not be empty.");
        }
        if (file.getSize() > maxAssetSizeBytes) {
            throw validationError("File size exceeds limit of " + (maxAssetSizeBytes / (1024 * 1024)) + " MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw validationError("Unable to read uploaded file bytes.");
        }

        if (bytes.length > maxAssetSizeBytes) {
            throw validationError("File size exceeds maximum allowed bytes.");
        }

        String originalName = sanitizeFilename(file.getOriginalFilename());
        DetectedFormat format = sniffImageFormat(bytes);
        if (format == null) {
            throw validationError("Unsupported or invalid image format. Allowed: PNG, JPEG, WebP.");
        }

        String ext = extractExtension(originalName);
        if (!ext.isEmpty() && !format.matchesExtension(ext)) {
            throw validationError("File extension does not match detected image content.");
        }

        Dimensions dimensions = readDimensions(bytes, format);
        if (dimensions.width() < MIN_DIMENSION_PX
                || dimensions.height() < MIN_DIMENSION_PX
                || dimensions.width() > MAX_DIMENSION_PX
                || dimensions.height() > MAX_DIMENSION_PX) {
            throw validationError("Image dimensions must be between 16x16 and 4096x4096 pixels.");
        }

        return new ValidatedImage(
                originalName,
                format.mimeType,
                format.canonicalExtension,
                dimensions.width(),
                dimensions.height(),
                bytes);
    }

    private Dimensions readDimensions(byte[] bytes, DetectedFormat format) {
        if (format == DetectedFormat.WEBP) {
            Dimensions webp = readWebpDimensions(bytes);
            if (webp == null) {
                throw validationError("Corrupted or unsupported WebP image.");
            }
            return webp;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw validationError("Image content could not be decoded.");
            }
            return new Dimensions(image.getWidth(), image.getHeight());
        } catch (IOException ex) {
            throw validationError("Image content could not be decoded.");
        }
    }

    private DetectedFormat sniffImageFormat(byte[] bytes) {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return DetectedFormat.PNG;
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return DetectedFormat.JPEG;
        }
        if (bytes.length >= 16
                && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return DetectedFormat.WEBP;
        }
        return null;
    }

    private Dimensions readWebpDimensions(byte[] bytes) {
        if (bytes.length < 30) {
            return null;
        }
        String chunk = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8 ".equals(chunk) && bytes.length >= 30) {
            if (bytes[23] != (byte) 0x9D || bytes[24] != 0x01 || bytes[25] != 0x2A) {
                return null;
            }
            int width = ((bytes[26] & 0xFF) | ((bytes[27] & 0xFF) << 8)) & 0x3FFF;
            int height = ((bytes[28] & 0xFF) | ((bytes[29] & 0xFF) << 8)) & 0x3FFF;
            return new Dimensions(width, height);
        }
        if ("VP8L".equals(chunk) && bytes.length >= 25 && bytes[20] == 0x2F) {
            int b1 = bytes[21] & 0xFF;
            int b2 = bytes[22] & 0xFF;
            int b3 = bytes[23] & 0xFF;
            int b4 = bytes[24] & 0xFF;
            int width = 1 + (((b2 & 0x3F) << 8) | b1);
            int height = 1 + (((b4 & 0x0F) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6));
            return new Dimensions(width, height);
        }
        if ("VP8X".equals(chunk) && bytes.length >= 30) {
            int width = 1 + ((bytes[24] & 0xFF) | ((bytes[25] & 0xFF) << 8) | ((bytes[26] & 0xFF) << 16));
            int height = 1 + ((bytes[27] & 0xFF) | ((bytes[28] & 0xFF) << 8) | ((bytes[29] & 0xFF) << 16));
            return new Dimensions(width, height);
        }
        return null;
    }

    private static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return "asset";
        }
        String base = rawFilename.replace('\\', '/');
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }
        String cleaned = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "asset" : cleaned;
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static AppException validationError(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message, Map.of("file", message));
    }

    private record Dimensions(int width, int height) {}

    private enum DetectedFormat {
        PNG("image/png", "png"),
        JPEG("image/jpeg", "jpg"),
        WEBP("image/webp", "webp");

        private final String mimeType;
        private final String canonicalExtension;

        DetectedFormat(String mimeType, String canonicalExtension) {
            this.mimeType = mimeType;
            this.canonicalExtension = canonicalExtension;
        }

        boolean matchesExtension(String ext) {
            return switch (this) {
                case PNG -> "png".equals(ext);
                case JPEG -> "jpg".equals(ext) || "jpeg".equals(ext);
                case WEBP -> "webp".equals(ext);
            };
        }
    }
}
