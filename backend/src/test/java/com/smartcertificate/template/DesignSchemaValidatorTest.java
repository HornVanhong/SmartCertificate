package com.smartcertificate.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.media.MediaAssetService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DesignSchemaValidatorTest {

    @Mock
    private MediaAssetService mediaAssetService;

    @Test
    void validatesStarterDesignAndReportsWarningWhenQrMissingOrTooSmall() {
        DesignSchemaValidator validator = new DesignSchemaValidator(mediaAssetService);
        StarterDesignFactory factory = new StarterDesignFactory();
        UUID orgId = UUID.randomUUID();

        DesignDocument starter = factory.createStarterDesign(TemplateOrientation.LANDSCAPE, 842.0, 595.0, "Academy");
        List<String> starterWarnings = validator.validate(starter, orgId, TemplateOrientation.LANDSCAPE, 842.0, 595.0);
        assertThat(starterWarnings).isEmpty();

        DesignDocument withoutQr = new DesignDocument(
                "1.0",
                starter.page(),
                starter.elements().stream()
                        .filter(el -> el.type() != DesignElementType.QR)
                        .toList());
        List<String> noQrWarnings = validator.validate(withoutQr, orgId, TemplateOrientation.LANDSCAPE, 842.0, 595.0);
        assertThat(noQrWarnings).anyMatch(w -> w.contains("QR verification"));

        DesignDocument tinyQr = new DesignDocument(
                "1.0",
                starter.page(),
                List.of(new DesignDocument.DesignElement(
                        "qr-small",
                        DesignElementType.QR,
                        "Small QR",
                        10,
                        10,
                        32,
                        32,
                        0,
                        1.0,
                        true,
                        false,
                        0,
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
        List<String> smallQrWarnings = validator.validate(tinyQr, orgId, TemplateOrientation.LANDSCAPE, 842.0, 595.0);
        assertThat(smallQrWarnings).anyMatch(w -> w.contains("smaller than 48pt"));
    }

    @Test
    void rejectsInvalidSchemaFontColorPlaceholderAndPageMismatch() {
        DesignSchemaValidator validator = new DesignSchemaValidator(mediaAssetService);
        UUID orgId = UUID.randomUUID();

        DesignDocument badDoc = new DesignDocument(
                "2.0",
                new DesignDocument.DesignPage(500.0, 500.0, "cm", TemplateOrientation.PORTRAIT, "not-hex", null),
                List.of(
                        new DesignDocument.DesignElement(
                                "dup-1",
                                DesignElementType.TEXT,
                                "Bad Text",
                                10,
                                10,
                                100,
                                30,
                                0,
                                1.0,
                                true,
                                false,
                                0,
                                "Hello {{bad key!}}",
                                "Comic Sans",
                                18.0,
                                "BOLD",
                                "NORMAL",
                                "#123",
                                "CENTER",
                                1.2,
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
                                "dup-1",
                                DesignElementType.IMAGE,
                                "Missing Asset Image",
                                10,
                                50,
                                100,
                                50,
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
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)));

        assertThatThrownBy(() -> validator.validate(badDoc, orgId, TemplateOrientation.LANDSCAPE, 842.0, 595.0))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(appEx.getFieldErrors())
                            .containsKeys(
                                    "designJson.schemaVersion",
                                    "designJson.page",
                                    "designJson.page.unit",
                                    "designJson.page.orientation",
                                    "designJson.page.backgroundColor",
                                    "designJson.elements[0].text",
                                    "designJson.elements[0].fontFamily",
                                    "designJson.elements[0].fill",
                                    "designJson.elements[1].id",
                                    "designJson.elements[1].assetId");
                });
    }

    @Test
    void enforcesCrossOrganizationAssetOwnershipCheck() {
        DesignSchemaValidator validator = new DesignSchemaValidator(mediaAssetService);
        UUID orgId = UUID.randomUUID();
        UUID foreignAssetId = UUID.randomUUID();

        doThrow(new AppException(ErrorCode.NOT_FOUND, "Media asset was not found."))
                .when(mediaAssetService)
                .assertAssetBelongsToOrganization(foreignAssetId, orgId);

        DesignDocument docWithForeignAsset = new DesignDocument(
                "1.0",
                new DesignDocument.DesignPage(842.0, 595.0, "pt", TemplateOrientation.LANDSCAPE, "#FFFFFF", null),
                List.of(new DesignDocument.DesignElement(
                        "logo-1",
                        DesignElementType.LOGO,
                        "Foreign Logo",
                        20,
                        20,
                        100,
                        60,
                        0,
                        1.0,
                        true,
                        false,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        foreignAssetId,
                        "CONTAIN",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));

        assertThatThrownBy(() ->
                        validator.validate(docWithForeignAsset, orgId, TemplateOrientation.LANDSCAPE, 842.0, 595.0))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(mediaAssetService).assertAssetBelongsToOrganization(foreignAssetId, orgId);
    }
}
