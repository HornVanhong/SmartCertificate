package com.smartcertificate.batch;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartcertificate.auth.JwtService;
import com.smartcertificate.auth.SecurityConfig;
import com.smartcertificate.common.config.SecurityProperties;
import com.smartcertificate.common.config.VerificationProperties;
import com.smartcertificate.common.exception.GlobalExceptionHandler;
import com.smartcertificate.common.filter.RequestIdFilter;
import com.smartcertificate.user.UserAccountService;
import com.smartcertificate.verification.PublicVerificationController;
import com.smartcertificate.verification.PublicVerificationResponse;
import com.smartcertificate.verification.PublicVerificationService;
import com.smartcertificate.verification.PublicVerificationStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {CertificateBatchController.class, PublicVerificationController.class})
@EnableConfigurationProperties({SecurityProperties.class, VerificationProperties.class})
@Import({
    SecurityConfig.class,
    JwtService.class,
    GlobalExceptionHandler.class,
    RequestIdFilter.class,
    com.smartcertificate.verification.PublicVerificationRateLimiter.class
})
@TestPropertySource(properties = "app.verification.rate-limit-per-minute=2")
@ActiveProfiles("test")
class BulkAndVerificationSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private CertificateBatchService certificateBatchService;

    @MockitoBean
    private PublicVerificationService publicVerificationService;

    @Test
    void rejectsUnauthenticatedBatchEndpointsAndAllowsPublicVerificationWithRateLimit() throws Exception {
        mockMvc.perform(get("/api/v1/certificate-batches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        when(publicVerificationService.verifyByToken("valid_opaque_token_123456"))
                .thenReturn(new PublicVerificationResponse(
                        PublicVerificationStatus.VALID,
                        "SC-2026-000001",
                        "Dara Example",
                        "Cloud Security",
                        "Alpha Academy",
                        Instant.now(),
                        null,
                        null));

        mockMvc.perform(get("/api/v1/public/verify/valid_opaque_token_123456")
                        .header("X-Forwarded-For", "198.51.100.42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALID"));

        mockMvc.perform(get("/api/v1/public/verify/valid_opaque_token_123456")
                        .header("X-Forwarded-For", "198.51.100.42"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/verify/valid_opaque_token_123456")
                        .header("X-Forwarded-For", "198.51.100.42"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void rejectsBatchGenerateForUsersWithoutCertificateIssuePermission() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        when(userAccountService.loadActiveMembership(userId, orgId))
                .thenReturn(Optional.of(new UserAccountService.MembershipAuthRecord(
                        userId, orgId, "viewer@org.test", "Viewer User", "VIEWER", Set.of("certificate:read"))));

        String token = jwtService.generateAccessToken(userId, orgId, "viewer@org.test");

        mockMvc.perform(post("/api/v1/certificate-batches/" + batchId + "/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        when(certificateBatchService.getBatch(eq(orgId), eq(userId), eq(false), eq(batchId)))
                .thenReturn(null);
        mockMvc.perform(get("/api/v1/certificate-batches/" + batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }
}
