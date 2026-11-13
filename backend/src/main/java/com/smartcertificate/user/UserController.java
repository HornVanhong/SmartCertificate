package com.smartcertificate.user;

import com.smartcertificate.common.api.ApiResponse;
import com.smartcertificate.common.api.PageMeta;
import com.smartcertificate.common.security.AuthenticatedPrincipal;
import com.smartcertificate.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users & Roles", description = "Organization-scoped user membership and role management")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    @Operation(summary = "List users belonging to the current organization")
    public ApiResponse<List<UserMembershipResponse>> listUsers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<UserMembershipResponse> result = userManagementService.listOrganizationUsers(
                SecurityContextUtils.requireOrganizationId(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return ApiResponse.ok(
                result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @PostMapping("/invitations")
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Invite a user to the current organization")
    public ResponseEntity<ApiResponse<InvitationResponse>> inviteUser(@Valid @RequestBody InviteUserRequest request) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        InvitationResponse invitation =
                userManagementService.inviteUser(principal.getOrganizationId(), principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(invitation));
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('user:read')")
    @Operation(summary = "List invitations for the current organization")
    public ApiResponse<List<InvitationResponse>> listInvitations(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<InvitationResponse> result = userManagementService.listInvitations(
                SecurityContextUtils.requireOrganizationId(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return ApiResponse.ok(
                result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Update permitted user fields and role within the current organization")
    public ApiResponse<UserMembershipResponse> updateUser(
            @PathVariable("id") UUID targetUserId, @Valid @RequestBody UpdateUserRequest request) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        return ApiResponse.ok(userManagementService.updateUser(
                principal.getOrganizationId(), principal.getUserId(), targetUserId, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Activate or deactivate a user membership within the current organization")
    public ApiResponse<UserMembershipResponse> updateUserStatus(
            @PathVariable("id") UUID targetUserId, @Valid @RequestBody UpdateUserStatusRequest request) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        return ApiResponse.ok(userManagementService.updateUserStatus(
                principal.getOrganizationId(), principal.getUserId(), targetUserId, request));
    }
}
