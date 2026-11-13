package com.smartcertificate.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartcertificate.common.config.StorageProperties;
import com.smartcertificate.common.exception.AppException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AssetValidationServiceTest {

    private final AssetValidationService validationService = new AssetValidationService(StorageProperties.ofLocal(
            "./data/test-storage", "test-secret-key-at-least-32-bytes-long", 300, 1024 * 1024));

    @Test
    void acceptsValidPngWithinDimensionBounds() throws Exception {
        byte[] pngBytes = createPng(64, 32);
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", pngBytes);

        AssetValidationService.ValidatedImage result = validationService.validateImage(file);
        assertEquals("image/png", result.mimeType());
        assertEquals(64, result.width());
        assertEquals(32, result.height());
    }

    @Test
    void rejectsSvgPayloadEvenWithPngExtension() {
        byte[] svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "evil.png", "image/png", svgBytes);

        assertThrows(AppException.class, () -> validationService.validateImage(file));
    }

    @Test
    void rejectsPngWithMismatchedExtension() throws Exception {
        byte[] pngBytes = createPng(32, 32);
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", pngBytes);

        assertThrows(AppException.class, () -> validationService.validateImage(file));
    }

    @Test
    void rejectsImageSmallerThanMinimumDimensions() throws Exception {
        byte[] tinyPng = createPng(8, 8);
        MockMultipartFile file = new MockMultipartFile("file", "tiny.png", "image/png", tinyPng);

        assertThrows(AppException.class, () -> validationService.validateImage(file));
    }

    private static byte[] createPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
