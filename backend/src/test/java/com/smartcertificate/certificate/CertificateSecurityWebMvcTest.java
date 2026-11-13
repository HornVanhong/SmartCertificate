package com.smartcertificate.certificate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import java.util.List;
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

@WebMvcTest(controllers = CertificateController.class)
@EnableConfigurationProperties(SecurityProperties.class)
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class, RequestIdFilter.class})
@ActiveProfiles("test")
class CertificateSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private CertificateService certificateService;

    @Test
    void rejectsUnauthenticatedCertificateAccess() throws Exception {
        mockMvc.perform(get("/api/v1/certificates"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void enforcesGeneralUserCreatorScopeAndBlocksRevocation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();

        when(userAccountService.loadActiveMembership(userId, orgId))
                .thenReturn(Optional.of(new UserAccountService.MembershipAuthRecord(
                        userId,
                        orgId,
                        "issuer@org.test",
                        "Issuer User",
                        "GENERAL_USER",
                        Set.of("org:read", "template:read", "certificate:read", "certificate:issue"))));

        when(certificateService.listCertificates(eq(orgId), eq(userId), eq(false), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        String token = jwtService.generateAccessToken(userId, orgId, "issuer@org.test");

        mockMvc.perform(get("/api/v1/certificates").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        verify(certificateService).listCertificates(eq(orgId), eq(userId), eq(false), any(), any(), any(), any());

        // GENERAL_USER cannot revoke certificates without certificate:revoke
        mockMvc.perform(post("/api/v1/certificates/" + certId + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Issued by mistake\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
