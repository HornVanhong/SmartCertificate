package com.smartcertificate.organization;

import com.smartcertificate.common.api.ApiResponse;
import com.smartcertificate.common.security.AuthenticatedPrincipal;
import com.smartcertificate.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/current")
@Tag(name = "Organizations", description = "Organization profile and branding settings")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('org:read')")
    @Operation(summary = "Get current organization profile")
    public ApiResponse<OrganizationResponse> getCurrentOrganization() {
        return ApiResponse.ok(organizationService.getCurrentOrganization(SecurityContextUtils.requireOrganizationId()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('org:update')")
    @Operation(summary = "Update current organization profile and branding")
    public ApiResponse<OrganizationResponse> updateCurrentOrganization(
            @Valid @RequestBody UpdateOrganizationRequest request) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        return ApiResponse.ok(organizationService.updateCurrentOrganization(
                principal.getOrganizationId(), principal.getUserId(), request));
    }
}
