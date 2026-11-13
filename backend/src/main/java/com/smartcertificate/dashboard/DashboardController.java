package com.smartcertificate.dashboard;

import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.common.api.ApiResponse;
import com.smartcertificate.common.security.AuthenticatedPrincipal;
import com.smartcertificate.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Organization summary metrics, issuance trends, and recent permitted activity")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('org:read', 'certificate:read', 'certificate:read_all')")
    @Operation(summary = "Get dashboard KPI summary scoped to organization or actor permissions")
    public ApiResponse<DashboardSummaryResponse> getSummary(Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        return ApiResponse.ok(
                dashboardService.getSummary(principal.getOrganizationId(), principal.getUserId(), canReadAll));
    }

    @GetMapping("/issuance-trends")
    @PreAuthorize("hasAnyAuthority('org:read', 'certificate:read', 'certificate:read_all')")
    @Operation(summary = "Get daily certificate issuance counts over a configurable window")
    public ApiResponse<List<IssuanceTrendPointResponse>> getIssuanceTrends(
            @RequestParam(value = "days", defaultValue = "30") int days, Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        return ApiResponse.ok(dashboardService.getIssuanceTrends(
                principal.getOrganizationId(), principal.getUserId(), canReadAll, days));
    }

    @GetMapping("/recent-activity")
    @PreAuthorize("hasAnyAuthority('org:read', 'certificate:read', 'certificate:read_all', 'audit:read')")
    @Operation(summary = "Get recent permitted audit activity for the workspace dashboard")
    public ApiResponse<List<AuditEventService.AuditLogResponse>> getRecentActivity(
            @RequestParam(value = "limit", defaultValue = "10") int limit, Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canAuditAll = hasAuthority(authentication, "audit:read");
        return ApiResponse.ok(dashboardService.getRecentActivity(
                principal.getOrganizationId(), principal.getUserId(), canAuditAll, limit));
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
