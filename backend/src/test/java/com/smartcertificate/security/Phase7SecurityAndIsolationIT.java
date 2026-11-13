package com.smartcertificate.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcertificate.auth.RegisterRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class Phase7SecurityAndIsolationIT {

    @TempDir
    static Path tempStorageDir;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.storage.local-root", () -> tempStorageDir.toAbsolutePath().toString());
        registry.add("app.auth.jwt-secret", () -> "phase7-security-integration-jwt-secret-key-32bytes");
        registry.add("app.storage.signed-url-secret", () -> "phase7-security-signed-url-secret-key-32bytes");
        registry.add("app.auth.cookie-secure", () -> "false");
        registry.add("app.verification.public-base-url", () -> "https://verify.smartcert.test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void enforcesSecurityHeadersUploadValidationTenantIsolationRbacAndDashboardMetrics() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgAAdminEmail = "admin-a-" + suffix + "@orga.test";
        String orgAToken = registerAdmin("Alice Admin", orgAAdminEmail, "Org Alpha " + suffix);
        String orgBToken = registerAdmin("Bob Admin", "admin-b-" + suffix + "@orgb.test", "Org Beta " + suffix);

        // 1. OWASP security headers and invalid JWT handling
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));

        mockMvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer tampered.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // 2. Upload security hardening: spoofed HTML, SVG, path traversal filename, forged/expired signed URL
        MockMultipartFile spoofedPng = new MockMultipartFile(
                "file",
                "exploit.png",
                "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/organizations/current/assets")
                        .file(spoofedPng)
                        .param("assetType", "LOGO")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().is4xxClientError());

        MockMultipartFile svgUpload = new MockMultipartFile(
                "file",
                "vector.svg",
                "image/svg+xml",
                "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>"
                        .getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/organizations/current/assets")
                        .file(svgUpload)
                        .param("assetType", "LOGO")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().is4xxClientError());

        byte[] validPng = createPng(120, 60);
        MockMultipartFile traversalNamedPng =
                new MockMultipartFile("file", "../../outside/logo.png", "image/png", validPng);
        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/organizations/current/assets")
                        .file(traversalNamedPng)
                        .param("assetType", "LOGO")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode assetNode = objectMapper
                .readTree(uploadRes.getResponse().getContentAsString())
                .get("data");
        String assetId = assetNode.get("id").asText();
        String signedUrl = assetNode.get("signedUrl").asText();

        try (var stream = Files.walk(tempStorageDir)) {
            List<Path> storedFiles = stream.filter(Files::isRegularFile).toList();
            assertThat(storedFiles).isNotEmpty();
            assertThat(storedFiles)
                    .allSatisfy(path -> assertThat(path.normalize().startsWith(tempStorageDir.normalize()))
                            .isTrue());
        }

        // Valid signed URL works; tampered/expired fails
        mockMvc.perform(get(signedUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        mockMvc.perform(get(
                        "/api/v1/organizations/current/assets/" + assetId + "/content?expires=9999999999&sig=forged"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/organizations/current/assets/" + assetId + "/content?expires=1000&sig=expired"))
                .andExpect(status().is4xxClientError());

        // 3. Create & publish template + issue certificate as Org A Admin
        MvcResult draftRes = mockMvc.perform(
                        post("/api/v1/templates")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "name": "Security Compliance Cert",
                                  "category": "Compliance",
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
                .andExpect(status().isOk());

        MvcResult certRes = mockMvc.perform(post("/api/v1/certificates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "templateId": "%s",
                                  "recipientName": "សុខ ដារ៉ា",
                                  "recipientEmail": "sok-%s@example.test",
                                  "courseName": "Application Security"
                                }
                                """
                                        .formatted(templateId, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        String adminCertId = objectMapper
                .readTree(certRes.getResponse().getContentAsString())
                .at("/data/id")
                .asText();

        // 4. Invite and activate a GENERAL_USER in Org A
        String memberEmail = "member-" + suffix + "@orga.test";
        MvcResult inviteRes = mockMvc.perform(post("/api/v1/users/invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "email": "%s",
                                  "fullName": "General Issuer",
                                  "roleName": "GENERAL_USER"
                                }
                                """
                                        .formatted(memberEmail)))
                .andExpect(status().isCreated())
                .andReturn();
        String invitationId = objectMapper
                .readTree(inviteRes.getResponse().getContentAsString())
                .at("/data/id")
                .asText();

        String rawInviteToken = "invite-token-" + suffix;
        jdbcTemplate.update(
                "UPDATE user_invitations SET token_hash = ? WHERE id = ?",
                sha256Hex(rawInviteToken),
                UUID.fromString(invitationId));

        MvcResult acceptRes = mockMvc.perform(post("/api/v1/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "token": "%s",
                                  "fullName": "General Issuer",
                                  "password": "StrongMemberPass!123"
                                }
                                """
                                        .formatted(rawInviteToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode memberSession = objectMapper
                .readTree(acceptRes.getResponse().getContentAsString())
                .get("data");
        String memberToken = memberSession.get("accessToken").asText();
        String memberUserId = memberSession.at("/session/userId").asText();

        // 5. GENERAL_USER privilege-escalation attempts must fail with 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/v1/organizations/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked Org\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users/invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@orga.test\",\"fullName\":\"X\",\"roleName\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized\",\"category\":\"General\",\"orientation\":\"LANDSCAPE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/certificates/" + adminCertId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not authorized\"}"))
                .andExpect(status().isForbidden());

        // 6. Dashboard RBAC scoping: GENERAL_USER sees PERSONAL scope (0 certs before own issue, then 1)
        mockMvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("PERSONAL"))
                .andExpect(jsonPath("$.data.totalCertificates").value(0));

        mockMvc.perform(post("/api/v1/certificates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "templateId": "%s",
                                  "recipientName": "Member Recipient",
                                  "recipientEmail": "mr-%s@example.test",
                                  "courseName": "Application Security"
                                }
                                """
                                        .formatted(templateId, suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("PERSONAL"))
                .andExpect(jsonPath("$.data.totalCertificates").value(1));

        mockMvc.perform(get("/api/v1/dashboard/recent-activity?limit=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].actorUserId", hasItem(memberUserId)))
                .andExpect(jsonPath("$.data[?(@.actorUserId != '" + memberUserId + "')]", empty()))
                .andExpect(jsonPath("$.data.length()").value(2));

        // 7. Org A Admin sees full ORGANIZATION dashboard KPIs, trends, recent activity, and filtered audit logs
        mockMvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ORGANIZATION"))
                .andExpect(jsonPath("$.data.totalCertificates").value(2))
                .andExpect(jsonPath("$.data.issuedCertificates").value(2))
                .andExpect(jsonPath("$.data.publishedTemplates").value(1));

        mockMvc.perform(get("/api/v1/dashboard/issuance-trends?days=7")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7));

        mockMvc.perform(get("/api/v1/audit-logs?action=CERTIFICATE_ISSUED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.pagination.totalElements").value(2));

        // 8. Org B isolation across dashboard, audit logs, templates, certificates
        mockMvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCertificates").value(0))
                .andExpect(jsonPath("$.data.totalTemplates").value(0));

        mockMvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].certificateId", not(hasItem(adminCertId))));

        mockMvc.perform(get("/api/v1/templates/" + templateId).header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/certificates/" + adminCertId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isNotFound());
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

    private static byte[] createPng(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
