package com.smartcertificate.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.smartcertificate.auth.RegisterRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class Phase6BulkAndVerificationIT {

    @TempDir
    static Path tempStorageDir;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.storage.local-root", () -> tempStorageDir.toAbsolutePath().toString());
        registry.add("app.auth.jwt-secret", () -> "phase6-integration-test-jwt-secret-key-32-bytes");
        registry.add("app.storage.signed-url-secret", () -> "phase6-integration-signed-url-secret-key-32b");
        registry.add("app.auth.cookie-secure", () -> "false");
        registry.add("app.verification.public-base-url", () -> "https://verify.smartcert.test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void executesBulkCsvWorkflowZipExportQrVerificationAndRevocationEndToEnd() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgAToken = registerAdmin("Alice Admin", "alice-" + suffix + "@orga.test", "Alpha Academy " + suffix);
        String orgBToken = registerAdmin("Bob Admin", "bob-" + suffix + "@orgb.test", "Beta Institute " + suffix);

        // 1. Create and publish template in Org A
        MvcResult draftRes = mockMvc.perform(
                        post("/api/v1/templates")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "name": "Bulk Certification Program",
                                  "category": "Engineering",
                                  "orientation": "LANDSCAPE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = objectMapper
                .readTree(draftRes.getResponse().getContentAsString())
                .at("/data/id")
                .asText();

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 2. Import CSV with 2 valid rows + 1 invalid row, verifying idempotency replay
        String csv = "Full Name,Email,Program,Date\n"
                + "Sokha Dara,sokha@example.org,Cybersecurity Cohort,2026-11-10\n"
                + " ,bad-email,Cybersecurity Cohort,invalid-date\n"
                + "Alex Rivera,alex@example.org,Cybersecurity Cohort,2026-11-11\n";

        MockMultipartFile csvFile =
                new MockMultipartFile("file", "cohort.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String batchKey = "batch-key-" + suffix;

        MvcResult importRes = mockMvc.perform(multipart("/api/v1/certificate-batches/import")
                        .file(csvFile)
                        .param("templateId", templateId)
                        .param("idempotencyKey", batchKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalRecords").value(3))
                .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andReturn();

        String batchId = objectMapper
                .readTree(importRes.getResponse().getContentAsString())
                .at("/data/id")
                .asText();

        // Idempotent replay
        mockMvc.perform(multipart("/api/v1/certificate-batches/import")
                        .file(csvFile)
                        .param("templateId", templateId)
                        .param("idempotencyKey", batchKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(batchId));

        // 3. Save explicit column mapping
        mockMvc.perform(
                        post("/api/v1/certificate-batches/" + batchId + "/mapping")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "mappings": {
                                    "recipientName": "Full Name",
                                    "recipientEmail": "Email",
                                    "courseName": "Program",
                                    "completionDate": "Date"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MAPPED"));

        // 4. Validate rows -> 2 valid, 1 invalid
        mockMvc.perform(post("/api/v1/certificate-batches/" + batchId + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALIDATED"))
                .andExpect(jsonPath("$.data.validRecords").value(2))
                .andExpect(jsonPath("$.data.invalidRecords").value(1));

        mockMvc.perform(get("/api/v1/certificate-batches/" + batchId + "/errors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].rowNumber").value(2))
                .andExpect(jsonPath("$.data[0].status").value("INVALID"));

        // 5. Queue async generation -> 202 Accepted
        mockMvc.perform(post("/api/v1/certificate-batches/" + batchId + "/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));

        // 6. Poll until terminal COMPLETED_WITH_ERRORS state
        JsonNode terminalBatch = awaitBatchTerminalState(orgAToken, batchId);
        assertThat(terminalBatch.at("/data/status").asText()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(terminalBatch.at("/data/successfulRecords").asInt()).isEqualTo(2);
        assertThat(terminalBatch.at("/data/zipAvailable").asBoolean()).isTrue();

        // 7. Tenant isolation: Org B cannot read or download Org A's batch
        mockMvc.perform(get("/api/v1/certificate-batches/" + batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isNotFound());

        // 8. Download ZIP and inspect manifest.csv + PDF entries
        MvcResult zipRes = mockMvc.perform(get("/api/v1/certificate-batches/" + batchId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, byte[]> entries = readZipEntries(zipRes.getResponse().getContentAsByteArray());
        assertThat(entries.keySet()).anyMatch(name -> name.endsWith(".pdf"));
        assertThat(entries).containsKey("manifest.csv");
        String manifest = new String(entries.get("manifest.csv"), StandardCharsets.UTF_8);
        assertThat(manifest).contains("Sokha Dara").contains("Alex Rivera");

        // 9. Inspect one generated certificate's PNG, decode its QR verification token, and test public verify +
        // revocation
        MvcResult certsRes = mockMvc.perform(
                        get("/api/v1/certificates").header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andReturn();
        String certId = objectMapper
                .readTree(certsRes.getResponse().getContentAsString())
                .at("/data/0/id")
                .asText();

        MvcResult pngRes = mockMvc.perform(get("/api/v1/certificates/" + certId + "/download?format=PNG&inline=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andReturn();
        String qrPayload = decodeQrPayloadFromPng(pngRes.getResponse().getContentAsByteArray());
        assertThat(qrPayload).contains("https://verify.smartcert.test/verify?token=");
        String rawToken = qrPayload.substring(qrPayload.indexOf("token=") + "token=".length());

        // Anonymous public verify -> VALID and zero private fields exposed
        mockMvc.perform(get("/api/v1/public/verify/" + rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.organizationName").value("Alpha Academy " + suffix))
                .andExpect(jsonPath("$.data.recipientEmail").doesNotExist())
                .andExpect(jsonPath("$.data.id").doesNotExist());

        // Revoke certificate and confirm public verify reflects REVOKED immediately
        mockMvc.perform(post("/api/v1/certificates/" + certId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Issued for wrong cohort\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/verify/" + rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.revokedReason").doesNotExist());

        // Unknown token -> generic 404 anti-enumeration
        mockMvc.perform(get("/api/v1/public/verify/unknown_invalid_token_000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    private JsonNode awaitBatchTerminalState(String token, String batchId) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000L;
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = mockMvc.perform(get("/api/v1/certificate-batches/" + batchId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode tree = objectMapper.readTree(res.getResponse().getContentAsString());
            String status = tree.at("/data/status").asText();
            if ("COMPLETED".equals(status) || "COMPLETED_WITH_ERRORS".equals(status) || "FAILED".equals(status)) {
                return tree;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for async batch completion");
    }

    private static Map<String, byte[]> readZipEntries(byte[] zipBytes) throws Exception {
        Map<String, byte[]> map = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                map.put(entry.getName(), zis.readAllBytes());
            }
        }
        return map;
    }

    private static String decodeQrPayloadFromPng(byte[] pngBytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        var source = new BufferedImageLuminanceSource(image);
        var bitmap = new BinaryBitmap(new HybridBinarizer(source));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    private String registerAdmin(String fullName, String email, String orgName) throws Exception {
        RegisterRequest req = new RegisterRequest(fullName, email, "StrongPass!123", orgName);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper
                .readTree(res.getResponse().getContentAsString())
                .at("/data/accessToken")
                .asText();
    }
}
