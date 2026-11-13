package com.smartcertificate.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartcertificate.auth.RegisterRequest;
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
class Phase3TemplateLifecycleIT {

    @TempDir
    static Path tempStorageDir;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.storage.local-root", () -> tempStorageDir.toAbsolutePath().toString());
        registry.add("app.auth.jwt-secret", () -> "phase3-integration-test-jwt-secret-key-32-bytes");
        registry.add("app.storage.signed-url-secret", () -> "phase3-integration-signed-url-secret-key-32b");
        registry.add("app.auth.cookie-secure", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void enforcesTemplateVersioningPreviewImmutabilityAndTenantIsolation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgAToken = registerAdmin("Alice Admin", "alice-" + suffix + "@orga.test", "Alpha " + suffix);
        String orgBToken = registerAdmin("Bob Admin", "bob-" + suffix + "@orgb.test", "Beta " + suffix);

        // Upload Org A logo asset to embed cleanly in templates and verify Org B rejection
        MockMultipartFile pngFile = new MockMultipartFile("file", "seal.png", "image/png", createPng(100, 100));
        MvcResult assetRes = mockMvc.perform(multipart("/api/v1/organizations/current/assets")
                        .file(pngFile)
                        .param("assetType", "STAMP")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isCreated())
                .andReturn();
        String orgAAssetId = objectMapper
                .readTree(assetRes.getResponse().getContentAsString())
                .at("/data/id")
                .asText();

        // 1. Create starter template in Org A
        MvcResult createRes = mockMvc.perform(
                        post("/api/v1/templates")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "name": "Engineering Excellence Award",
                                  "description": "Awarded for outstanding capstone engineering work",
                                  "category": "Academic",
                                  "orientation": "LANDSCAPE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.currentVersion").value(1))
                .andExpect(jsonPath("$.data.publishedVersion").doesNotExist())
                .andExpect(jsonPath("$.data.designJson.schemaVersion").value("1.0"))
                .andReturn();

        JsonNode createdData = objectMapper
                .readTree(createRes.getResponse().getContentAsString())
                .get("data");
        String templateId = createdData.get("id").asText();
        ObjectNode v1Design = (ObjectNode) createdData.get("designJson").deepCopy();

        // 2. Search/filter with empty/null params AND explicit filters against real PostgreSQL
        mockMvc.perform(get("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .param("page", "0")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(templateId)));

        mockMvc.perform(get("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .param("q", "engineering")
                        .param("status", "DRAFT")
                        .param("category", "academic")
                        .param("orientation", "LANDSCAPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(templateId)));

        // 3. Preview with Khmer + English sample values and embedded Org A asset
        ObjectNode v1WithStamp = v1Design.deepCopy();
        v1WithStamp
                .withArray("elements")
                .addObject()
                .put("id", "el-stamp-1")
                .put("type", "STAMP")
                .put("name", "Official Seal")
                .put("x", 650)
                .put("y", 420)
                .put("width", 90)
                .put("height", 90)
                .put("rotation", 0)
                .put("opacity", 1.0)
                .put("visible", true)
                .put("locked", false)
                .put("zIndex", 20)
                .put("assetId", orgAAssetId)
                .put("fit", "CONTAIN");

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PreviewTemplateRequest(
                                objectMapper.treeToValue(v1WithStamp, DesignDocument.class),
                                java.util.Map.of("recipientName", "សុខ ដារ៉ា / Sok Dara")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolvedPlaceholders.recipientName").value("សុខ ដារ៉ា / Sok Dara"))
                .andExpect(jsonPath("$.data.assetUrls['" + orgAAssetId + "']").isNotEmpty());

        // 4. Save draft v1 with Org A asset, then Publish v1
        mockMvc.perform(put("/api/v1/templates/" + templateId + "/draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildDraftPayload("Engineering Excellence Award", "Academic", v1WithStamp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(1))
                .andExpect(jsonPath("$.data.assetUrls['" + orgAAssetId + "']").isNotEmpty());

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.currentVersion").value(1))
                .andExpect(jsonPath("$.data.publishedVersion").value(1));

        // 5. Editing a PUBLISHED template must create version 2 as DRAFT while v1 stays immutable
        ObjectNode v2Design = v1WithStamp.deepCopy();
        ((ObjectNode) v2Design.get("page")).put("backgroundColor", "#F8FAFC");

        mockMvc.perform(put("/api/v1/templates/" + templateId + "/draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildDraftPayload("Engineering Excellence Award v2", "Academic", v2Design)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.currentVersion").value(2))
                .andExpect(jsonPath("$.data.publishedVersion").value(1));

        MvcResult v1Immutable = mockMvc.perform(get("/api/v1/templates/" + templateId + "/versions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.designJson.page.backgroundColor").value("#FFFFFF"))
                .andReturn();
        assertThat(v1Immutable.getResponse().getContentAsString()).contains("#FFFFFF");

        // 6. Duplicate template
        MvcResult dupRes = mockMvc.perform(post("/api/v1/templates/" + templateId + "/duplicate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Award Duplicate\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Award Duplicate"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.currentVersion").value(1))
                .andReturn();
        String duplicatedId = objectMapper
                .readTree(dupRes.getResponse().getContentAsString())
                .at("/data/id")
                .asText();
        assertThat(duplicatedId).isNotEqualTo(templateId);

        // 7. Cross-organization isolation: Org B cannot list, read, draft-update, preview, publish, or embed Org A
        // assets
        mockMvc.perform(get("/api/v1/templates").header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(templateId))));

        mockMvc.perform(get("/api/v1/templates/" + templateId).header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken))
                .andExpect(status().isNotFound());

        ObjectNode orgBHijackDesign = v1WithStamp.deepCopy();
        mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "name": "Hijack Attempt",
                                  "orientation": "LANDSCAPE",
                                  "designJson": %s
                                }
                                """
                                        .formatted(objectMapper.writeValueAsString(orgBHijackDesign))))
                .andExpect(status().isNotFound());

        // 8. Archive original template and verify edits/re-publish return 409 CONFLICT
        mockMvc.perform(post("/api/v1/templates/" + templateId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(put("/api/v1/templates/" + templateId + "/draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildDraftPayload("Edit After Archive", "Academic", v2Design)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAToken))
                .andExpect(status().isConflict());
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

    private String buildDraftPayload(String name, String category, JsonNode designJson) throws Exception {
        return """
                {
                  "name": "%s",
                  "description": "Updated description",
                  "category": "%s",
                  "orientation": "LANDSCAPE",
                  "pageWidth": 842.00,
                  "pageHeight": 595.00,
                  "designJson": %s
                }
                """
                .formatted(name, category, objectMapper.writeValueAsString(designJson));
    }

    private static byte[] createPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
