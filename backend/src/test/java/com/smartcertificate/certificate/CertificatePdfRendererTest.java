package com.smartcertificate.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartcertificate.media.MediaAssetService;
import com.smartcertificate.template.DesignDocument;
import com.smartcertificate.template.DesignElementType;
import com.smartcertificate.template.TemplateOrientation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
class CertificatePdfRendererTest {

    @Mock
    private MediaAssetService mediaAssetService;

    @Test
    void rendersPdfAndPngWithEnglishKhmerImageAssetAndQr() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID logoAssetId = UUID.randomUUID();
        byte[] sampleLogoPng = createSamplePng(80, 40);
        when(mediaAssetService.loadOrganizationAssetBytes(logoAssetId, orgId)).thenReturn(sampleLogoPng);

        QrCodeGeneratorService qrService = new QrCodeGeneratorService();
        CertificatePdfRenderer renderer = new CertificatePdfRenderer(mediaAssetService, qrService);

        DesignDocument design = new DesignDocument(
                "1.0",
                new DesignDocument.DesignPage(842, 595, "pt", TemplateOrientation.LANDSCAPE, "#FFFFFF", null),
                List.of(
                        new DesignDocument.DesignElement(
                                "border-1",
                                DesignElementType.SHAPE,
                                "Frame",
                                28,
                                28,
                                786,
                                539,
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
                                3.0,
                                0.0,
                                null,
                                null,
                                null),
                        new DesignDocument.DesignElement(
                                "logo-1",
                                DesignElementType.LOGO,
                                "Logo",
                                60,
                                56,
                                90,
                                45,
                                0,
                                1.0,
                                true,
                                false,
                                1,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                logoAssetId,
                                "CONTAIN",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        new DesignDocument.DesignElement(
                                "title-en",
                                DesignElementType.TEXT,
                                "Title",
                                100,
                                130,
                                642,
                                48,
                                0,
                                1.0,
                                true,
                                false,
                                2,
                                "Certificate awarded to {{recipientName}}",
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
                                "title-km",
                                DesignElementType.TEXT,
                                "Khmer Line",
                                100,
                                210,
                                642,
                                48,
                                0,
                                1.0,
                                true,
                                false,
                                3,
                                "វិញ្ញាបនបត្របញ្ចប់វគ្គសិក្សា - {{courseName}}",
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
                                "qr-1",
                                DesignElementType.QR,
                                "QR",
                                700,
                                440,
                                84,
                                84,
                                0,
                                1.0,
                                true,
                                false,
                                4,
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

        CertificatePdfRenderer.RenderedCertificate rendered = renderer.render(
                design,
                orgId,
                Map.of(
                        "recipientName", "សុខ ដារ៉ា / Sok Dara",
                        "courseName", "Enterprise Security",
                        "verificationUrl", "https://verify.example/verify?token=abc123"));

        String pdfHeader = new String(rendered.pdfBytes(), 0, 5, StandardCharsets.US_ASCII);
        assertThat(pdfHeader).isEqualTo("%PDF-");
        assertThat(rendered.pdfBytes().length).isGreaterThan(5000);

        BufferedImage png = ImageIO.read(new ByteArrayInputStream(rendered.pngBytes()));
        assertThat(png).isNotNull();
        assertThat(png.getWidth()).isGreaterThan(1000);
        assertThat(png.getHeight()).isGreaterThan(700);
    }

    private static byte[] createSamplePng(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
