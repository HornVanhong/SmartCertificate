package com.smartcertificate.certificate;

import com.smartcertificate.audit.AuditEventService;
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificates", description = "Individual certificate issuance, download, revocation, and audit history")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('certificate:read', 'certificate:read_all')")
    @Operation(summary = "Search and paginate organization certificates")
    public ApiResponse<List<CertificateSummaryResponse>> listCertificates(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "status", required = false) CertificateStatus status,
            @RequestParam(value = "templateId", required = false) UUID templateId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");

        Page<CertificateSummaryResponse> result = certificateService.listCertificates(
                principal.getOrganizationId(),
                principal.getUserId(),
                canReadAll,
                query,
                status,
                templateId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));

        return ApiResponse.ok(
                result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('certificate:issue')")
    @Operation(summary = "Issue a single certificate idempotently with rendered PDF and PNG artifacts")
    public ResponseEntity<ApiResponse<CertificateDetailResponse>> issueCertificate(
            @Valid @RequestBody IssueCertificateRequest request) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        CertificateService.IssueOutcome outcome =
                certificateService.issueCertificate(principal.getOrganizationId(), principal.getUserId(), request);
        HttpStatus status = outcome.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(outcome.certificate()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('certificate:read', 'certificate:read_all')")
    @Operation(summary = "Get certificate details")
    public ApiResponse<CertificateDetailResponse> getCertificate(
            @PathVariable("id") UUID certificateId, Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        return ApiResponse.ok(certificateService.getCertificate(
                principal.getOrganizationId(), principal.getUserId(), canReadAll, certificateId));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyAuthority('certificate:read', 'certificate:read_all')")
    @Operation(summary = "Download rendered certificate PDF or PNG")
    public ResponseEntity<byte[]> downloadCertificate(
            @PathVariable("id") UUID certificateId,
            @RequestParam(value = "format", defaultValue = "PDF") CertificateFormat format,
            @RequestParam(value = "inline", defaultValue = "false") boolean inline,
            Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");

        CertificateService.DownloadPayload payload = certificateService.downloadCertificate(
                principal.getOrganizationId(), principal.getUserId(), canReadAll, certificateId, format);

        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(payload.fileName())
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(payload.bytes());
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('certificate:revoke')")
    @Operation(summary = "Revoke an issued certificate with mandatory reason")
    public ApiResponse<CertificateDetailResponse> revokeCertificate(
            @PathVariable("id") UUID certificateId, @Valid @RequestBody RevokeCertificateRequest request) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        return ApiResponse.ok(certificateService.revokeCertificate(
                principal.getOrganizationId(), principal.getUserId(), certificateId, request));
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyAuthority('certificate:read', 'certificate:read_all', 'audit:read')")
    @Operation(summary = "List audit history for a specific certificate")
    public ApiResponse<List<AuditEventService.AuditLogResponse>> getCertificateAudit(
            @PathVariable("id") UUID certificateId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll =
                hasAuthority(authentication, "certificate:read_all") || hasAuthority(authentication, "audit:read");

        Page<AuditEventService.AuditLogResponse> result = certificateService.listCertificateAuditLogs(
                principal.getOrganizationId(),
                principal.getUserId(),
                canReadAll,
                certificateId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));

        return ApiResponse.ok(
                result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
