package com.smartcertificate.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;
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
class Phase2OrganizationIsolationIT {

    @TempDir
    static Path tempStorageDir;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.storage.local-root", () -> tempStorageDir.toAbsolutePath().toString());
        registry.add("app.auth.jwt-secret", () -> "integration-test-jwt-secret-key-at-least-32-bytes-long");
        registry.add("app.storage.signed-url-secret", () -> "integration-test-signed-url-secret-key-at-least-32-bytes");
        registry.add("app.auth.cookie-secure", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void enforcesOrganizationIsolationRefreshRotationAndAssetSecurity() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgAEmail = "alice-" + suffix + "@orga.test";
        String orgBEmail = "bob-" + suffix + "@orgb.test";

        // 1. Register Org A and Org B admins
        JsonNode orgASession = registerUser("Alice Admin", orgAEmail, "StrongPass123!", "Org Alpha " + suffix);
        String orgAToken = orgASession.get("accessToken").asText();
        String orgARefresh = orgASession.get("refreshToken").asText();
        String aliceUserId = orgASession.get("session").get("userId").asText();

        JsonNode orgBSession = registerUser("Bob Admin", orgBEmail, "StrongPass123!", "Org Beta " + suffix);
        String orgBToken = orgBSession.get("accessToken").asText();
        String bobUserId = orgBSession.get("session").get("userId").asText();

        // 2. Verify /auth/me resolves each user's own organization
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(orgAEmail))
                .andExpect(jsonPath("$.data.roleName").value("ADMIN"))
                .andExpect(jsonPath("$.data.organization.name").value("Org Alpha " + suffix));

        // 3. Verify refresh token rotation & reuse revocation
        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + orgARefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String rotatedRefresh = objectMapper
                .readTree(refreshRes.getResponse().getContentAsString())
                .at("/data/refreshToken")
                .asText();

        // Reusing already-rotated refresh token must fail AND revoke the rotated token too
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + orgARefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // Re-login Alice for remaining checks
        orgAToken = loginUser(orgAEmail, "StrongPass123!").get("accessToken").asText();

        // 4. Org A uploads a PNG logo
        byte[] pngBytes = createPng(120, 60);
        MockMultipartFile logoFile = new MockMultipartFile("file", "alpha-logo.png", "image/png", pngBytes);
        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/organizations/current/assets")
                        .file(logoFile)
                        .param("assetType", "LOGO")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.width").value(120))
                .andExpect(jsonPath("$.data.signedUrl").isNotEmpty())
                .andReturn();

        JsonNode assetData = objectMapper
                .readTree(uploadRes.getResponse().getContentAsString())
                .get("data");
        String orgAAssetId = assetData.get("id").asText();
        String orgASignedUrl = assetData.get("signedUrl").asText();

        // Signed URL returns image bytes publicly only when signature is valid
        mockMvc.perform(get(orgASignedUrl)).andExpect(status().isOk());

        // Org B CANNOT read Org A's asset content via session or assign Org A's logoAssetId
        mockMvc.perform(get("/api/v1/organizations/current/assets/" + orgAAssetId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/organizations/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Org Beta Updated\",\"logoAssetId\":\"" + orgAAssetId + "\"}"))
                .andExpect(status().isNotFound());

        // Org B asset listing must not include Org A's asset
        mockMvc.perform(get("/api/v1/organizations/current/assets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(orgAAssetId))));

        // 5. Cross-organization user isolation: Org B admin cannot modify or view Org A's Alice
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].userId", not(hasItem(aliceUserId))))
                .andExpect(jsonPath("$.data[*].userId", hasItem(bobUserId)));

        mockMvc.perform(patch("/api/v1/users/" + aliceUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/users/" + aliceUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNotFound());

        // 6. Org A invites a GENERAL_USER and cannot self-deactivate as last ADMIN
        mockMvc.perform(patch("/api/v1/users/" + aliceUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/users/invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member-" + suffix
                                + "@orga.test\",\"fullName\":\"General Member\",\"roleName\":\"GENERAL_USER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.roleName").value("GENERAL_USER"));
    }

    private JsonNode registerUser(String fullName, String email, String password, String orgName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(fullName, email, password, orgName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode loginUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static byte[] createPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
