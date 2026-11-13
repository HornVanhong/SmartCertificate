package com.smartcertificate.verification;

import com.smartcertificate.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public Verification", description = "Anonymous verification endpoint for QR certificate tokens")
public class PublicVerificationController {

    private final PublicVerificationService publicVerificationService;
    private final PublicVerificationRateLimiter rateLimiter;

    public PublicVerificationController(
            PublicVerificationService publicVerificationService, PublicVerificationRateLimiter rateLimiter) {
        this.publicVerificationService = publicVerificationService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/verify/{token}")
    @Operation(summary = "Verify a certificate by opaque QR token")
    public ApiResponse<PublicVerificationResponse> verifyCertificate(
            @PathVariable("token") String token, HttpServletRequest request) {
        rateLimiter.checkLimit(resolveClientKey(request));
        return ApiResponse.ok(publicVerificationService.verifyByToken(token));
    }

    private static String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String firstHop = (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!firstHop.isEmpty()) {
                return firstHop;
            }
        }
        return request.getRemoteAddr();
    }
}
