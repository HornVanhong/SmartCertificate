package com.smartcertificate.certificate;

import com.smartcertificate.media.MediaAssetService;
import com.smartcertificate.template.DesignDocument;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Renders canonical DesignDocument payloads into PDF and rasterized PNG certificate artifacts. */
@Service
class CertificatePdfRenderer {

    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");
    private static final float BEZIER_CIRCLE_KAPPA = 0.5522847498f;
    private static final float PNG_DPI = 150f;

    private final MediaAssetService mediaAssetService;
    private final QrCodeGeneratorService qrCodeGeneratorService;
    private final byte[] interRegularFontBytes;
    private final byte[] khmerRegularFontBytes;
    private final byte[] khmerBoldFontBytes;

    record RenderedCertificate(byte[] pdfBytes, byte[] pngBytes) {}

    private record FontSet(
            PDFont sansRegular,
            PDFont sansBold,
            PDFont sansItalic,
            PDFont khmerRegular,
            PDFont khmerBold,
            PDFont serifRegular,
            PDFont serifBold,
            PDFont serifItalic,
            PDFont monoRegular,
            PDFont monoBold) {}

    private record TextRun(PDFont font, String text) {}

    CertificatePdfRenderer(MediaAssetService mediaAssetService, QrCodeGeneratorService qrCodeGeneratorService) {
        this.mediaAssetService = mediaAssetService;
        this.qrCodeGeneratorService = qrCodeGeneratorService;
        this.interRegularFontBytes = loadFontResource("fonts/Inter-Regular.ttf");
        this.khmerRegularFontBytes = loadFontResource("fonts/NotoSansKhmer-Regular.ttf");
        this.khmerBoldFontBytes = loadFontResource("fonts/NotoSansKhmer-Bold.ttf");
    }

    RenderedCertificate render(DesignDocument design, UUID organizationId, Map<String, String> placeholders) {
        float pageWidth = (float) Math.max(100.0, design.page().width());
        float pageHeight = (float) Math.max(100.0, design.page().height());
        Map<UUID, byte[]> assetCache = new HashMap<>();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            document.addPage(page);

            FontSet fonts = createFontSet(document);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                Color bgColor = parseHexColor(design.page().backgroundColor(), Color.WHITE);
                cs.setNonStrokingColor(bgColor);
                cs.addRect(0, 0, pageWidth, pageHeight);
                cs.fill();

                if (design.page().backgroundAssetId() != null) {
                    byte[] bgBytes = loadAssetCached(design.page().backgroundAssetId(), organizationId, assetCache);
                    if (bgBytes != null) {
                        PDImageXObject bgImg = PDImageXObject.createFromByteArray(document, bgBytes, "bg");
                        cs.drawImage(bgImg, 0, 0, pageWidth, pageHeight);
                    }
                }

                List<DesignDocument.DesignElement> sortedElements = design.elements().stream()
                        .filter(DesignDocument.DesignElement::visible)
                        .sorted(Comparator.comparingInt(DesignDocument.DesignElement::zIndex))
                        .toList();

                for (DesignDocument.DesignElement element : sortedElements) {
                    renderElement(document, cs, pageHeight, element, organizationId, placeholders, fonts, assetCache);
                }
            }

            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            document.save(pdfOut);
            byte[] pdfBytes = pdfOut.toByteArray();

            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage renderedImage = renderer.renderImageWithDPI(0, PNG_DPI, ImageType.RGB);
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            ImageIO.write(renderedImage, "png", pngOut);

            return new RenderedCertificate(pdfBytes, pngOut.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render certificate PDF/PNG artifacts", ex);
        }
    }

    private void renderElement(
            PDDocument document,
            PDPageContentStream cs,
            float pageHeight,
            DesignDocument.DesignElement element,
            UUID organizationId,
            Map<String, String> placeholders,
            FontSet fonts,
            Map<UUID, byte[]> assetCache)
            throws IOException {
        float x = (float) element.x();
        float width = (float) Math.max(1.0, element.width());
        float height = (float) Math.max(1.0, element.height());
        float pdfY = pageHeight - (float) element.y() - height;

        cs.saveGraphicsState();

        float opacity = (float) Math.max(0.0, Math.min(1.0, element.opacity()));
        if (opacity < 0.999f) {
            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(opacity);
            gs.setStrokingAlphaConstant(opacity);
            cs.setGraphicsStateParameters(gs);
        }

        if (Math.abs(element.rotation()) > 0.01) {
            float centerX = x + (width / 2f);
            float centerY = pdfY + (height / 2f);
            double radians = -Math.toRadians(element.rotation());
            cs.transform(Matrix.getRotateInstance(radians, centerX, centerY));
        }

        switch (element.type()) {
            case SHAPE -> renderShape(cs, element, x, pdfY, width, height);
            case TEXT -> renderText(cs, element, x, pdfY, width, height, placeholders, fonts);
            case QR -> renderQr(document, cs, element, x, pdfY, width, height, placeholders);
            case IMAGE, LOGO, SIGNATURE, STAMP -> {
                if (element.assetId() != null) {
                    byte[] imgBytes = loadAssetCached(element.assetId(), organizationId, assetCache);
                    if (imgBytes != null) {
                        PDImageXObject image = PDImageXObject.createFromByteArray(document, imgBytes, element.id());
                        drawFittedImage(cs, image, x, pdfY, width, height, element.fit());
                    }
                }
            }
        }

        cs.restoreGraphicsState();
    }

    private void renderShape(
            PDPageContentStream cs,
            DesignDocument.DesignElement element,
            float x,
            float pdfY,
            float width,
            float height)
            throws IOException {
        String shapeKind = element.shapeKind() != null ? element.shapeKind() : "RECTANGLE";
        boolean hasFill = element.fill() != null && !"transparent".equalsIgnoreCase(element.fill());
        float strokeWidth =
                element.strokeWidth() != null ? element.strokeWidth().floatValue() : 0f;
        boolean hasStroke = element.stroke() != null && strokeWidth > 0f;

        if ("LINE".equals(shapeKind)) {
            Color strokeColor = parseHexColor(element.stroke(), parseHexColor(element.fill(), Color.BLACK));
            cs.setStrokingColor(strokeColor);
            cs.setLineWidth(Math.max(0.75f, strokeWidth > 0 ? strokeWidth : 1.5f));
            float midY = pdfY + height / 2f;
            cs.moveTo(x, midY);
            cs.lineTo(x + width, midY);
            cs.stroke();
            return;
        }

        if ("CIRCLE".equals(shapeKind)) {
            appendEllipsePath(cs, x + width / 2f, pdfY + height / 2f, width / 2f, height / 2f);
        } else {
            cs.addRect(x, pdfY, width, height);
        }

        if (hasFill && hasStroke) {
            cs.setNonStrokingColor(parseHexColor(element.fill(), Color.WHITE));
            cs.setStrokingColor(parseHexColor(element.stroke(), Color.BLACK));
            cs.setLineWidth(strokeWidth);
            cs.fillAndStroke();
        } else if (hasFill) {
            cs.setNonStrokingColor(parseHexColor(element.fill(), Color.WHITE));
            cs.fill();
        } else if (hasStroke) {
            cs.setStrokingColor(parseHexColor(element.stroke(), Color.BLACK));
            cs.setLineWidth(strokeWidth);
            cs.stroke();
        }
    }

    private void renderText(
            PDPageContentStream cs,
            DesignDocument.DesignElement element,
            float x,
            float pdfY,
            float width,
            float height,
            Map<String, String> placeholders,
            FontSet fonts)
            throws IOException {
        String resolved = resolvePlaceholders(element.text(), placeholders)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        if (resolved.isBlank()) {
            return;
        }

        float fontSize = element.fontSize() != null ? element.fontSize().floatValue() : 14f;
        float lineHeightMultiplier =
                element.lineHeight() != null ? element.lineHeight().floatValue() : 1.25f;
        float leading = fontSize * lineHeightMultiplier;
        boolean bold = "BOLD".equalsIgnoreCase(element.fontWeight());
        boolean italic = "ITALIC".equalsIgnoreCase(element.fontStyle());
        PDFont primaryFont = pickPrimaryFont(element.fontFamily(), bold, italic, fonts);
        PDFont fallbackFont = bold ? fonts.khmerBold() : fonts.khmerRegular();
        Color textColor = parseHexColor(element.fill(), new Color(0x0F, 0x17, 0x2A));

        List<String> lines = wrapParagraphs(resolved, width, fontSize, primaryFont, fallbackFont, fonts.sansRegular());
        float totalBlockHeight = lines.size() * leading;
        float startY = pdfY + height - ((height - totalBlockHeight) / 2f) - fontSize * 0.85f;
        // Clamp top so tall multi-line text never starts above its container box
        startY = Math.min(pdfY + height - fontSize * 0.85f, startY);

        cs.setNonStrokingColor(textColor);
        String align = element.textAlign() != null ? element.textAlign() : "CENTER";

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float baselineY = startY - (i * leading);
            if (baselineY < pdfY - fontSize * 0.2f) {
                break;
            }
            List<TextRun> runs = splitIntoEncodableRuns(line, primaryFont, fallbackFont, fonts.sansRegular());
            float lineWidth = computeRunsWidth(runs, fontSize);

            float drawX =
                    switch (align) {
                        case "LEFT" -> x;
                        case "RIGHT" -> x + Math.max(0f, width - lineWidth);
                        default -> x + Math.max(0f, (width - lineWidth) / 2f);
                    };

            float cursorX = drawX;
            for (TextRun run : runs) {
                cs.beginText();
                cs.setFont(run.font(), fontSize);
                cs.newLineAtOffset(cursorX, baselineY);
                cs.showText(run.text());
                cs.endText();
                cursorX += measureRunWidth(run.font(), run.text(), fontSize);
            }
        }
    }

    private void renderQr(
            PDDocument document,
            PDPageContentStream cs,
            DesignDocument.DesignElement element,
            float x,
            float pdfY,
            float width,
            float height,
            Map<String, String> placeholders)
            throws IOException {
        String qrPayload = resolvePlaceholders(element.qrDataTemplate(), placeholders);
        int targetPixels = Math.max(180, (int) Math.ceil(Math.max(width, height) * 3.0));
        byte[] qrPng = qrCodeGeneratorService.generateQrPng(
                qrPayload, targetPixels, element.foregroundColor(), element.backgroundColor());
        PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qrPng, element.id());
        drawFittedImage(cs, qrImage, x, pdfY, width, height, "CONTAIN");
    }

    private void drawFittedImage(
            PDPageContentStream cs,
            PDImageXObject image,
            float boxX,
            float boxY,
            float boxWidth,
            float boxHeight,
            String fit)
            throws IOException {
        if ("COVER".equalsIgnoreCase(fit)) {
            cs.saveGraphicsState();
            cs.addRect(boxX, boxY, boxWidth, boxHeight);
            cs.clip();
            float scale =
                    Math.max(boxWidth / Math.max(1f, image.getWidth()), boxHeight / Math.max(1f, image.getHeight()));
            float drawW = image.getWidth() * scale;
            float drawH = image.getHeight() * scale;
            float drawX = boxX + (boxWidth - drawW) / 2f;
            float drawY = boxY + (boxHeight - drawH) / 2f;
            cs.drawImage(image, drawX, drawY, drawW, drawH);
            cs.restoreGraphicsState();
            return;
        }

        float scale = Math.min(boxWidth / Math.max(1f, image.getWidth()), boxHeight / Math.max(1f, image.getHeight()));
        float drawW = image.getWidth() * scale;
        float drawH = image.getHeight() * scale;
        float drawX = boxX + (boxWidth - drawW) / 2f;
        float drawY = boxY + (boxHeight - drawH) / 2f;
        cs.drawImage(image, drawX, drawY, drawW, drawH);
    }

    private List<String> wrapParagraphs(
            String text, float maxWidth, float fontSize, PDFont primary, PDFont fallbackKhmer, PDFont fallbackSans) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            String[] words = paragraph.split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                float candidateWidth = computeRunsWidth(
                        splitIntoEncodableRuns(candidate, primary, fallbackKhmer, fallbackSans), fontSize);
                if (candidateWidth <= maxWidth || current.isEmpty()) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }
        return lines;
    }

    private List<TextRun> splitIntoEncodableRuns(
            String text, PDFont primary, PDFont fallbackKhmer, PDFont fallbackSans) {
        List<TextRun> runs = new ArrayList<>();
        PDFont currentFont = null;
        StringBuilder buffer = new StringBuilder();

        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            String glyphStr = text.substring(i, i + charCount);
            i += charCount;

            PDFont chosen = selectFontForCodePoint(glyphStr, primary, fallbackKhmer, fallbackSans);
            if (chosen == null) {
                continue;
            }
            if (currentFont == null) {
                currentFont = chosen;
            } else if (currentFont != chosen) {
                runs.add(new TextRun(currentFont, buffer.toString()));
                buffer.setLength(0);
                currentFont = chosen;
            }
            buffer.append(glyphStr);
        }

        if (!buffer.isEmpty() && currentFont != null) {
            runs.add(new TextRun(currentFont, buffer.toString()));
        }
        return runs;
    }

    private PDFont selectFontForCodePoint(
            String singleGlyph, PDFont primary, PDFont fallbackKhmer, PDFont fallbackSans) {
        if (canEncode(primary, singleGlyph)) return primary;
        if (fallbackKhmer != null && canEncode(fallbackKhmer, singleGlyph)) return fallbackKhmer;
        if (fallbackSans != null && canEncode(fallbackSans, singleGlyph)) return fallbackSans;
        return null;
    }

    private static boolean canEncode(PDFont font, String s) {
        if (font == null) return false;
        try {
            font.encode(s);
            return true;
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException ex) {
            return false;
        }
    }

    private float computeRunsWidth(List<TextRun> runs, float fontSize) {
        float total = 0f;
        for (TextRun run : runs) {
            total += measureRunWidth(run.font(), run.text(), fontSize);
        }
        return total;
    }

    private static float measureRunWidth(PDFont font, String text, float fontSize) {
        try {
            return (font.getStringWidth(text) / 1000f) * fontSize;
        } catch (IOException | IllegalArgumentException ex) {
            return 0f;
        }
    }

    private FontSet createFontSet(PDDocument doc) throws IOException {
        PDFont sansRegular = interRegularFontBytes != null
                ? PDType0Font.load(doc, new ByteArrayInputStream(interRegularFontBytes), true)
                : new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont sansBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDFont sansItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        PDFont khmerRegular = khmerRegularFontBytes != null
                ? PDType0Font.load(doc, new ByteArrayInputStream(khmerRegularFontBytes), true)
                : sansRegular;
        PDFont khmerBold = khmerBoldFontBytes != null
                ? PDType0Font.load(doc, new ByteArrayInputStream(khmerBoldFontBytes), true)
                : khmerRegular;

        PDFont serifRegular = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
        PDFont serifBold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
        PDFont serifItalic = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);

        PDFont monoRegular = new PDType1Font(Standard14Fonts.FontName.COURIER);
        PDFont monoBold = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);

        return new FontSet(
                sansRegular,
                sansBold,
                sansItalic,
                khmerRegular,
                khmerBold,
                serifRegular,
                serifBold,
                serifItalic,
                monoRegular,
                monoBold);
    }

    private PDFont pickPrimaryFont(String fontFamily, boolean bold, boolean italic, FontSet fonts) {
        if ("Noto Sans Khmer".equalsIgnoreCase(fontFamily)) {
            return bold ? fonts.khmerBold() : fonts.khmerRegular();
        }
        if ("Georgia".equalsIgnoreCase(fontFamily) || "Times New Roman".equalsIgnoreCase(fontFamily)) {
            if (bold) return fonts.serifBold();
            if (italic) return fonts.serifItalic();
            return fonts.serifRegular();
        }
        if ("Courier New".equalsIgnoreCase(fontFamily)) {
            return bold ? fonts.monoBold() : fonts.monoRegular();
        }
        if (bold) return fonts.sansBold();
        if (italic) return fonts.sansItalic();
        return fonts.sansRegular();
    }

    private byte[] loadAssetCached(UUID assetId, UUID organizationId, Map<UUID, byte[]> cache) {
        if (cache.containsKey(assetId)) {
            return cache.get(assetId);
        }
        byte[] bytes = mediaAssetService.loadOrganizationAssetBytes(assetId, organizationId);
        cache.put(assetId, bytes);
        return bytes;
    }

    private static String resolvePlaceholders(String rawText, Map<String, String> placeholders) {
        if (rawText == null) return "";
        Matcher matcher = PLACEHOLDER_TOKEN.matcher(rawText);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = placeholders.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static void appendEllipsePath(PDPageContentStream cs, float cx, float cy, float rx, float ry)
            throws IOException {
        float ox = rx * BEZIER_CIRCLE_KAPPA;
        float oy = ry * BEZIER_CIRCLE_KAPPA;
        cs.moveTo(cx - rx, cy);
        cs.curveTo(cx - rx, cy + oy, cx - ox, cy + ry, cx, cy + ry);
        cs.curveTo(cx + ox, cy + ry, cx + rx, cy + oy, cx + rx, cy);
        cs.curveTo(cx + rx, cy - oy, cx + ox, cy - ry, cx, cy - ry);
        cs.curveTo(cx - ox, cy - ry, cx - rx, cy - oy, cx - rx, cy);
        cs.closePath();
    }

    private static Color parseHexColor(String hex, Color fallback) {
        if (hex == null || !hex.matches("^#[0-9a-fA-F]{6}$")) {
            return fallback;
        }
        return Color.decode(hex);
    }

    private static byte[] loadFontResource(String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            return null;
        }
    }
}
