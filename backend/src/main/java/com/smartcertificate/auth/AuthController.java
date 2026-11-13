package com.smartcertificate.auth;

import com.smartcertificate.common.api.ApiResponse;
import com.smartcertificate.common.security.AuthenticatedPrincipal;
import com.smartcertificate.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication, session refresh rotation, and account lifecycle")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Issue CSRF token cookie for browser sessions")
    public ApiResponse<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok(Map.of("headerName", csrfToken.getHeaderName()));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new administrator and organization")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> register(
            @Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthSessionResponse session = authService.register(request);
        authCookieService.attachAuthCookies(response, session.accessToken(), session.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(session));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    public ApiResponse<AuthSessionResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthSessionResponse session = authService.login(request);
        authCookieService.attachAuthCookies(response, session.accessToken(), session.refreshToken());
        return ApiResponse.ok(session);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue a fresh access token")
    public ApiResponse<AuthSessionResponse> refresh(
            @RequestBody(required = false) RefreshRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        String rawRefreshToken = resolveRefreshToken(requestBody, request);
        AuthSessionResponse session = authService.refresh(rawRefreshToken);
        authCookieService.attachAuthCookies(response, session.accessToken(), session.refreshToken());
        return ApiResponse.ok(session);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke refresh token and clear session cookies")
    public ApiResponse<Map<String, Boolean>> logout(
            @RequestBody(required = false) RefreshRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        String rawRefreshToken = resolveRefreshToken(requestBody, request);
        authService.logout(rawRefreshToken);
        authCookieService.clearAuthCookies(response);
        return ApiResponse.ok(Map.of("loggedOut", true));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset token")
    public ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.ok(
                Map.of("message", "If an active account exists for that email, reset instructions were generated."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete password reset using a reset token")
    public ApiResponse<Map<String, Boolean>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok(Map.of("reset", true));
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Accept an organization invitation and activate membership")
    public ApiResponse<AuthSessionResponse> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request, HttpServletResponse response) {
        AuthSessionResponse session = authService.acceptInvitation(request);
        authCookieService.attachAuthCookies(response, session.accessToken(), session.refreshToken());
        return ApiResponse.ok(session);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the current authenticated user, organization, and permissions")
    public ApiResponse<AuthMeResponse> me() {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        return ApiResponse.ok(authService.getCurrentSession(principal.getUserId(), principal.getOrganizationId()));
    }

    private String resolveRefreshToken(RefreshRequest requestBody, HttpServletRequest request) {
        if (requestBody != null
                && requestBody.refreshToken() != null
                && !requestBody.refreshToken().isBlank()) {
            return requestBody.refreshToken();
        }
        return authCookieService
                .extractCookie(request, AuthCookieService.REFRESH_COOKIE_NAME)
                .orElse(null);
    }
}
