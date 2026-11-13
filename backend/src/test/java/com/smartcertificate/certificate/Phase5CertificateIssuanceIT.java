package com.smartcertificate.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcertificate.auth.RegisterRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class Phase5CertificateIssuanceIT {

    @TempDir
    static Path tempStorageDir;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.storage.local-root", () -> tempStorageDir.toAbsolutePath().toString());
        registry.add("app.auth.jwt-secret", () -> "phase5-integration-test-jwt-secret-key-32-bytes");
        registry.add("app.storage.signed-url-secret", () -> "phase5-integration-signed-url-secret-key-32b");
        registry.add("app.auth.cookie-secure", () -> "false");
        registry.add("app.verification.public-base-url", () -> "https://verify.smartcert.test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void enforcesIdempotentIssuancePdfPngDownloadsRevocationAuditAndTenantIsolation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgAToken = registerAdmin("Alice Admin", "alice-" + suffix + "@orga.test", "Alpha Academy " + suffix);
        String orgBToken = registerAdmin("Bob Admin", "bob-" + suffix + "@orgb.test", "Beta Institute " + suffix);

        // 1. Create a draft template and confirm issuance is blocked until published
        MvcResult draftRes = mockMvc.perform(
                        post("/api/v1/templates")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "name": "Cloud Security Specialist",
                                  "category": "Security",
                                  "orientation": "LANDSCAPE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = objectMapper
                .readTree(draftRes.getResponse().getContentAsString())
                .at("/data/id")
                .asText();

        String idempotencyKey = "issue-" + suffix;
        String issueBody =
                """
                {
                  "templateId": "%s",
                  "recipientName": "សុខ ដារ៉ា / Sok Dara",
                  "recipientEmail": "dara@example.com",
                  "courseName": "Zero-Trust Cloud Engineering",
                  "completionDate": "2026-11-10",
                  "idempotencyKey": "%s"
                }
                """
                        .formatted(templateId, idempotencyKey);

        mockMvc.perform(post("/api/v1/certificates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody))
                .andExpect(status().isConflict());

        // 2. Publish template and issue certificate
        mockMvc.perform(post("/api/v1/templates/" + templateId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedVersion").value(1));

        MvcResult issueRes = mockMvc.perform(post("/api/v1/certificates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.pdfAvailable").value(true))
                .andExpect(jsonPath("$.data.pngAvailable").value(true))
                .andExpect(jsonPath("$.data.certificateNumber").isNotEmpty())
                .andReturn();

        JsonNode issuedData = objectMapper
                .readTree(issueRes.getResponse().getContentAsString())
                .get("data");
        String certId = issuedData.get("id").asText();
        String certNumber = issuedData.get("certificateNumber").asText();
        assertThat(certNumber).startsWith("SC-");

        // 3. Re-submitting exact idempotencyKey must return existing certificate without duplicating
        mockMvc.perform(post("/api/v1/certificates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(certId))
                .andExpect(jsonPath("$.data.certificateNumber").value(certNumber));

        // 4. Download PDF and PNG artifacts
        MvcResult pdfRes = mockMvc.perform(get("/api/v1/certificates/" + certId + "/download")
                        .param("format", "PDF")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        byte[] pdfBytes = pdfRes.getResponse().getContentAsByteArray();
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        MvcResult pngRes = mockMvc.perform(get("/api/v1/certificates/" + certId + "/download")
                        .param("format", "PNG")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();
        assertThat(pngRes.getResponse().getContentAsByteArray().length).isGreaterThan(5000);

        // 5. Search & filter certificates with optional/null parameters
        mockMvc.perform(get("/api/v1/certificates").header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(certId)));

        mockMvc.perform(get("/api/v1/certificates")
                        .param("q", "zero-trust")
                        .param("status", "ISSUED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(certId)));

        // 6. Cross-organization isolation: Org B cannot list, read, download, revoke, or view audit logs
        mockMvc.perform(get("/api/v1/certificates").header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(certId))));

        mockMvc.perform(get("/api/v1/certificates/" + certId).header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/certificates/" + certId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/certificates/" + certId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cross-org attack\"}"))
                .andExpect(status().isNotFound());

        // 7. Revoke requires reason, succeeds once, blocks duplicate revocation, and records audit history
        mockMvc.perform(post("/api/v1/certificates/" + certId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/certificates/" + certId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Superseded by corrected record\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.revokedReason").value("Superseded by corrected record"));

        mockMvc.perform(post("/api/v1/certificates/" + certId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Duplicate revoke\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/certificates/" + certId + "/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].action", hasItem("CERTIFICATE_ISSUED")))
                .andExpect(jsonPath("$.data[*].action", hasItem("CERTIFICATE_DOWNLOADED")))
                .andExpect(jsonPath("$.data[*].action", hasItem("CERTIFICATE_REVOKED")));
    }

    private String registerAdmin(String fullName, String email, String orgName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(fullName, email, "StrongPass123!", orgName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken")
                .asText();
    }
}
