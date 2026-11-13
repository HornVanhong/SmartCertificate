package com.smartcertificate.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcertificate.media.MediaAssetService;
import com.smartcertificate.template.DesignDocument;
import com.smartcertificate.template.DesignElementType;
import com.smartcertificate.template.TemplateOrientation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdfVisualRegressionTest {

    @Mock
    private MediaAssetService mediaAssetService;

    @Test
    void rendersDeterministicLandscapeBilingualCertificateWithinExpectedVisualBounds() throws Exception {
        CertificatePdfRenderer renderer = new CertificatePdfRenderer(mediaAssetService, new QrCodeGeneratorService());
        DesignDocument design = buildBilingualDesign(TemplateOrientation.LANDSCAPE, 842, 595);
        Map<String, String> placeholders = Map.of(
                "recipientName", "សុខ ដារ៉ា / Sok Dara",
                "courseName", "Applied Cryptography & Platform Security",
                "verificationUrl", "https://verify.smartcert.test/verify?token=visual_regression_token_01");

        CertificatePdfRenderer.RenderedCertificate firstRender =
                renderer.render(design, UUID.randomUUID(), placeholders);
        CertificatePdfRenderer.RenderedCertificate secondRender =
                renderer.render(design, UUID.randomUUID(), placeholders);

        assertThat(new String(firstRender.pdfBytes(), 0, 5, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");

        BufferedImage img1 = ImageIO.read(new ByteArrayInputStream(firstRender.pngBytes()));
        BufferedImage img2 = ImageIO.read(new ByteArrayInputStream(secondRender.pngBytes()));
        assertThat(img1).isNotNull();
        assertThat(img2).isNotNull();

        // 842x595 pt rendered at 150 DPI -> 1754x1240 px (allow rounding +/- 2px across Font/Java2D updates)
        assertThat(img1.getWidth()).isBetween(1752, 1756);
        assertThat(img1.getHeight()).isBetween(1238, 1242);
        assertThat(img1.getWidth()).isEqualTo(img2.getWidth());
        assertThat(img1.getHeight()).isEqualTo(img2.getHeight());

        // Pixel-by-pixel determinism across repeated renders
        assertThat(computeRgbExactMatchRatio(img1, img2)).isGreaterThan(0.999);

        // Background color should dominate the clean outer corner (x=5..20, y=5..20 px)
        double cornerWhiteRatio = computeNearWhiteRatio(img1, 5, 5, 20, 20);
        assertThat(cornerWhiteRatio).isGreaterThan(0.98);

        // Top navy frame border around pt (28, 28) -> ~58 px
        double borderInkRatio = computeNonWhiteInkRatio(img1, 120, 54, 1500, 72);
        assertThat(borderInkRatio).isGreaterThan(0.20);

        // English recipient headline region (x=100..742pt, y=130..185pt -> 208..1545px, 270..385px)
        double englishInkRatio = computeNonWhiteInkRatio(img1, 220, 270, 1520, 385);
        assertThat(englishInkRatio).isBetween(0.015, 0.45);

        // Khmer subtitle region (x=100..742pt, y=225..285pt -> 208..1545px, 468..594px)
        double khmerInkRatio = computeNonWhiteInkRatio(img1, 220, 465, 1520, 600);
        assertThat(khmerInkRatio).isBetween(0.01, 0.45);

        // QR code region (x=700..784pt, y=440..524pt -> 1458..1633px, 916..1092px)
        double qrInkRatio = computeNonWhiteInkRatio(img1, 1460, 920, 1630, 1090);
        assertThat(qrInkRatio).isBetween(0.20, 0.80);
    }

    @Test
    void rendersPortraitCanvasWithExpectedAspectRatioAndNonEmptyContent() throws Exception {
        CertificatePdfRenderer renderer = new CertificatePdfRenderer(mediaAssetService, new QrCodeGeneratorService());
        DesignDocument portraitDesign = buildBilingualDesign(TemplateOrientation.PORTRAIT, 595, 842);

        CertificatePdfRenderer.RenderedCertificate rendered = renderer.render(
                portraitDesign,
                UUID.randomUUID(),
                Map.of(
                        "recipientName", "នារី ចាន់ / Nary Chan",
                        "courseName", "Cloud Architecture",
                        "verificationUrl", "https://verify.smartcert.test/verify?token=portrait_token_02"));

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(rendered.pngBytes()));
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isBetween(1238, 1242);
        assertThat(img.getHeight()).isBetween(1752, 1756);
        assertThat(computeNonWhiteInkRatio(img, 180, 260, 1080, 620)).isGreaterThan(0.01);
    }

    private static DesignDocument buildBilingualDesign(TemplateOrientation orientation, double width, double height) {
        double contentWidth = width - 160;
        double qrX = width - 140;
        double qrY = height - 150;
        return new DesignDocument(
                "1.0",
                new DesignDocument.DesignPage(width, height, "pt", orientation, "#FFFFFF", null),
                List.of(
                        new DesignDocument.DesignElement(
                                "frame-border",
                                DesignElementType.SHAPE,
                                "Frame",
                                28,
                                28,
                                width - 56,
                                height - 56,
                                0,
                                1.0,
                                true,
                                true,
                                0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "transparent",
                                null,
                                null,
                                null,
                                null,
                                "RECTANGLE",
                                "#142D4E",
                                4.0,
                                0.0,
                                null,
                                null,
                                null),
                        new DesignDocument.DesignElement(
                                "recipient-en",
                                DesignElementType.TEXT,
                                "Recipient Headline",
                                80,
                                130,
                                contentWidth,
                                56,
                                0,
                                1.0,
                                true,
                                false,
                                1,
                                "Awarded to {{recipientName}}",
                                "Inter",
                                24.0,
                                "BOLD",
                                "NORMAL",
                                "#142D4E",
                                "CENTER",
                                1.25,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        new DesignDocument.DesignElement(
                                "khmer-line",
                                DesignElementType.TEXT,
                                "Khmer Subtitle",
                                80,
                                225,
                                contentWidth,
                                60,
                                0,
                                1.0,
                                true,
                                false,
                                2,
                                "វិញ្ញាបនបត្របញ្ចប់វគ្គសិក្សា {{courseName}}",
                                "Noto Sans Khmer",
                                20.0,
                                "BOLD",
                                "NORMAL",
                                "#0F172A",
                                "CENTER",
                                1.3,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        new DesignDocument.DesignElement(
                                "qr-verify",
                                DesignElementType.QR,
                                "Verification QR",
                                qrX,
                                qrY,
                                84,
                                84,
                                0,
                                1.0,
                                true,
                                false,
                                3,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "{{verificationUrl}}",
                                "#142D4E",
                                "#FFFFFF")));
    }

    private static double computeRgbExactMatchRatio(BufferedImage a, BufferedImage b) {
        int total = a.getWidth() * a.getHeight();
        int matching = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) == b.getRGB(x, y)) {
                    matching++;
                }
            }
        }
        return (double) matching / total;
    }

    private static double computeNonWhiteInkRatio(BufferedImage image, int minX, int minY, int maxX, int maxY) {
        int clampedMinX = Math.max(0, minX);
        int clampedMinY = Math.max(0, minY);
        int clampedMaxX = Math.min(image.getWidth() - 1, maxX);
        int clampedMaxY = Math.min(image.getHeight() - 1, maxY);

        int total = 0;
        int ink = 0;
        for (int y = clampedMinY; y <= clampedMaxY; y++) {
            for (int x = clampedMinX; x <= clampedMaxX; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r < 235 || g < 235 || b < 235) {
                    ink++;
                }
                total++;
            }
        }
        return total == 0 ? 0.0 : (double) ink / total;
    }

    private static double computeNearWhiteRatio(BufferedImage image, int minX, int minY, int maxX, int maxY) {
        return 1.0 - computeNonWhiteInkRatio(image, minX, minY, maxX, maxY);
    }
}
