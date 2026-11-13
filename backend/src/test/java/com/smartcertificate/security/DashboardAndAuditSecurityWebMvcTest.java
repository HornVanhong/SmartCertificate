package com.smartcertificate.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartcertificate.audit.AuditController;
import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.auth.JwtService;
import com.smartcertificate.auth.SecurityConfig;
import com.smartcertificate.common.config.SecurityProperties;
import com.smartcertificate.common.exception.GlobalExceptionHandler;
import com.smartcertificate.common.filter.RequestIdFilter;
import com.smartcertificate.dashboard.DashboardController;
import com.smartcertificate.dashboard.DashboardService;
import com.smartcertificate.dashboard.DashboardSummaryResponse;
import com.smartcertificate.dashboard.IssuanceTrendPointResponse;
import com.smartcertificate.user.UserAccountService;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {DashboardController.class, AuditController.class})
@EnableConfigurationProperties(SecurityProperties.class)
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class, RequestIdFilter.class})
@ActiveProfiles("test")
class DashboardAndAuditSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private AuditEventService auditEventService;

    @Test
    void rejectsUnauthenticatedAccessAndIncludesOwaspSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void forbidsGeneralUserFromReadingOrganizationAuditLogsAndUsesPersonalDashboardScope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(userAccountService.loadActiveMembership(userId, orgId))
                .thenReturn(Optional.of(new UserAccountService.MembershipAuthRecord(
                        userId,
                        orgId,
                        "member@org.test",
                        "Member User",
                        "GENERAL_USER",
                        Set.of("org:read", "template:read", "certificate:read", "certificate:issue"))));

        when(dashboardService.getSummary(orgId, userId, false))
                .thenReturn(new DashboardSummaryResponse(3, 2, 1, 2, 4, 2, 2, 1, 0, "PERSONAL"));
        when(dashboardService.getRecentActivity(orgId, userId, false, 5)).thenReturn(List.of());

        String token = jwtService.generateAccessToken(userId, orgId, "member@org.test");

        mockMvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("PERSONAL"))
                .andExpect(jsonPath("$.data.totalCertificates").value(3));

        mockMvc.perform(get("/api/v1/dashboard/recent-activity?limit=5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        verify(dashboardService).getSummary(orgId, userId, false);
        verify(dashboardService).getRecentActivity(orgId, userId, false, 5);
    }

    @Test
    void allowsAdminToReadOrganizationDashboardAndPaginatedAuditLogs() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(userAccountService.loadActiveMembership(userId, orgId))
                .thenReturn(Optional.of(new UserAccountService.MembershipAuthRecord(
                        userId,
                        orgId,
                        "admin@org.test",
                        "Org Admin",
                        "ADMIN",
                        Set.of("org:read", "certificate:read", "certificate:read_all", "audit:read"))));

        when(dashboardService.getSummary(orgId, userId, true))
                .thenReturn(new DashboardSummaryResponse(12, 10, 2, 8, 5, 3, 2, 2, 1, "ORGANIZATION"));
        when(dashboardService.getIssuanceTrends(orgId, userId, true, 14))
                .thenReturn(List.of(new IssuanceTrendPointResponse(LocalDate.of(2026, 11, 1), 4)));
        when(auditEventService.listOrganizationAuditLogs(
                        eq(orgId), isNull(), eq("CERTIFICATE_ISSUED"), eq("Alice"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new AuditEventService.AuditLogResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        userId,
                        "CERTIFICATE_ISSUED",
                        "Issued certificate CERT-001",
                        Map.of("recipientName", "Alice"),
                        Instant.now()))));

        String token = jwtService.generateAccessToken(userId, orgId, "admin@org.test");

        mockMvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ORGANIZATION"))
                .andExpect(jsonPath("$.data.issuedCertificates").value(10));

        mockMvc.perform(get("/api/v1/dashboard/issuance-trends?days=14")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].issuedCount").value(4));

        mockMvc.perform(get("/api/v1/audit-logs?action=CERTIFICATE_ISSUED&q=Alice&page=0&size=20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("CERTIFICATE_ISSUED"))
                .andExpect(jsonPath("$.meta.pagination.totalElements").value(1));

        verify(dashboardService).getSummary(orgId, userId, true);
    }
}
