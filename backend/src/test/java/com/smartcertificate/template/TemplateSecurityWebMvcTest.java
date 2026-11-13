package com.smartcertificate.template;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartcertificate.auth.JwtService;
import com.smartcertificate.auth.SecurityConfig;
import com.smartcertificate.common.config.SecurityProperties;
import com.smartcertificate.common.exception.GlobalExceptionHandler;
import com.smartcertificate.common.filter.RequestIdFilter;
import com.smartcertificate.user.UserAccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TemplateController.class)
@EnableConfigurationProperties(SecurityProperties.class)
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class, RequestIdFilter.class})
@ActiveProfiles("test")
class TemplateSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private TemplateService templateService;

    @Test
    void rejectsUnauthenticatedTemplateListing() throws Exception {
        mockMvc.perform(get("/api/v1/templates"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void enforcesTemplateRbacForGeneralUserAndTemplateEditor() throws Exception {
        UUID generalUserId = UUID.randomUUID();
        UUID editorUserId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();

        when(userAccountService.loadActiveMembership(generalUserId, orgId))
                .thenReturn(Optional.of(new UserAccountService.MembershipAuthRecord(
                        generalUserId,
                        orgId,
                        "viewer@org.test",
                        "Viewer User",
                        "GENERAL_USER",
                        Set.of("org:read", "template:read", "certificate:read"))));

        when(userAccountService.loadActiveMembership(editorUserId, orgId))
                .thenReturn(Optional.of(new UserAccountService.MembershipAuthRecord(
                        editorUserId,
                        orgId,
                        "editor@org.test",
                        "Editor User",
                        "TEMPLATE_EDITOR",
                        Set.of("org:read", "template:read", "template:write", "certificate:read"))));

        when(templateService.listTemplates(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        String generalToken = jwtService.generateAccessToken(generalUserId, orgId, "viewer@org.test");
        String editorToken = jwtService.generateAccessToken(editorUserId, orgId, "editor@org.test");

        // GENERAL_USER can read templates, cannot create or publish
        mockMvc.perform(get("/api/v1/templates").header(HttpHeaders.AUTHORIZATION, "Bearer " + generalToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blocked\",\"orientation\":\"LANDSCAPE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // TEMPLATE_EDITOR can create draft, cannot publish
        when(templateService.createDraft(any(), any(), any()))
                .thenReturn(new TemplateDetailResponse(
                        templateId,
                        "Draft",
                        null,
                        null,
                        TemplateOrientation.LANDSCAPE,
                        new BigDecimal("842.00"),
                        new BigDecimal("595.00"),
                        TemplateStatus.DRAFT,
                        1,
                        null,
                        new StarterDesignFactory().createStarterDesign(TemplateOrientation.LANDSCAPE, 842, 595, "Org"),
                        Map.of(),
                        List.of(),
                        Instant.now(),
                        Instant.now()));

        mockMvc.perform(post("/api/v1/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Draft\",\"orientation\":\"LANDSCAPE\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
