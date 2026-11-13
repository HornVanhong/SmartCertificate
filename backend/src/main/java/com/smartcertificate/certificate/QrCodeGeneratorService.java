package com.smartcertificate.certificate;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
class QrCodeGeneratorService {

    byte[] generateQrPng(String payload, int pixelSize, String foregroundHex, String backgroundHex) {
        String safePayload = (payload == null || payload.isBlank()) ? "https://smartcertificate.local" : payload;
        int size = Math.max(96, Math.min(pixelSize, 1024));
        Color fg = parseHexColor(foregroundHex, new Color(0x14, 0x2D, 0x4E));
        Color bg = parseHexColor(backgroundHex, Color.WHITE);

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(
                    safePayload,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    Map.of(
                            EncodeHintType.ERROR_CORRECTION,
                            ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN,
                            1,
                            EncodeHintType.CHARACTER_SET,
                            "UTF-8"));

            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            int fgRgb = fg.getRGB();
            int bgRgb = bg.getRGB();
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? fgRgb : bgRgb);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Failed to generate verification QR code", ex);
        }
    }

    private static Color parseHexColor(String hex, Color fallback) {
        if (hex == null || !hex.matches("^#[0-9a-fA-F]{6}$")) {
            return fallback;
        }
        return Color.decode(hex);
    }
}
