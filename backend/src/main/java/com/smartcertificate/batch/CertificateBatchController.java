package com.smartcertificate.batch;

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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/certificate-batches")
@Tag(
        name = "Certificate Batches",
        description = "CSV/XLSX bulk roster import, validation, async generation, and ZIP export")
public class CertificateBatchController {

    private final CertificateBatchService batchService;

    public CertificateBatchController(CertificateBatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('certificate:issue', 'certificate:read', 'certificate:read_all')")
    @Operation(summary = "List bulk certificate generation batches scoped to the organization")
    public ApiResponse<List<BatchSummaryResponse>> listBatches(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        Page<BatchSummaryResponse> result = batchService.listBatches(
                principal.getOrganizationId(),
                principal.getUserId(),
                canReadAll,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return ApiResponse.ok(
                result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('certificate:issue')")
    @Operation(summary = "Upload CSV or XLSX roster file to start a bulk certificate batch")
    public ResponseEntity<ApiResponse<BatchDetailResponse>> importBatch(
            @RequestParam("templateId") UUID templateId,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey,
            @RequestPart("file") MultipartFile file) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        CertificateBatchService.ImportOutcome outcome = batchService.importBatch(
                principal.getOrganizationId(), principal.getUserId(), templateId, idempotencyKey, file);
        HttpStatus status = outcome.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(outcome.batch()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('certificate:issue', 'certificate:read', 'certificate:read_all')")
    @Operation(summary = "Get batch status, progress, headers, and column mappings")
    public ApiResponse<BatchDetailResponse> getBatch(@PathVariable("id") UUID id, Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        return ApiResponse.ok(
                batchService.getBatch(principal.getOrganizationId(), principal.getUserId(), canReadAll, id));
    }

    @PostMapping("/{id}/mapping")
    @PreAuthorize("hasAuthority('certificate:issue')")
    @Operation(summary = "Save spreadsheet column-to-placeholder mapping")
    public ApiResponse<BatchDetailResponse> saveMapping(
            @PathVariable("id") UUID id,
            @Valid @RequestBody BatchColumnMappingRequest request,
            Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        return ApiResponse.ok(batchService.saveMapping(
                principal.getOrganizationId(), principal.getUserId(), canReadAll, id, request));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('certificate:issue')")
    @Operation(summary = "Validate all batch rows against mapped fields")
    public ApiResponse<BatchDetailResponse> validateBatch(@PathVariable("id") UUID id, Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        return ApiResponse.ok(
                batchService.validateBatch(principal.getOrganizationId(), principal.getUserId(), canReadAll, id));
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority('certificate:issue')")
    @Operation(summary = "Queue asynchronous batch certificate generation")
    public ResponseEntity<ApiResponse<BatchDetailResponse>> generateBatch(
            @PathVariable("id") UUID id, Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        BatchDetailResponse batch =
                batchService.generateBatch(principal.getOrganizationId(), principal.getUserId(), canReadAll, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(batch));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('certificate:issue')")
    @Operation(summary = "Retry failed rows idempotently in an asynchronous worker")
    public ResponseEntity<ApiResponse<BatchDetailResponse>> retryFailedItems(
            @PathVariable("id") UUID id, Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        BatchDetailResponse batch =
                batchService.retryFailedItems(principal.getOrganizationId(), principal.getUserId(), canReadAll, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(batch));
    }

    @GetMapping("/{id}/errors")
    @PreAuthorize("hasAnyAuthority('certificate:issue', 'certificate:read', 'certificate:read_all')")
    @Operation(summary = "List invalid or failed batch rows with row-level error messages")
    public ApiResponse<List<BatchErrorItemResponse>> listBatchErrors(
            @PathVariable("id") UUID id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        Page<BatchErrorItemResponse> result = batchService.listBatchErrors(
                principal.getOrganizationId(),
                principal.getUserId(),
                canReadAll,
                id,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return ApiResponse.ok(
                result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyAuthority('certificate:issue', 'certificate:read', 'certificate:read_all')")
    @Operation(summary = "Download generated ZIP archive of batch certificates")
    public ResponseEntity<byte[]> downloadBatchZip(@PathVariable("id") UUID id, Authentication authentication) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        boolean canReadAll = hasAuthority(authentication, "certificate:read_all");
        CertificateBatchService.ZipDownloadPayload payload =
                batchService.downloadBatchZip(principal.getOrganizationId(), principal.getUserId(), canReadAll, id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(payload.contentType()));
        headers.setContentLength(payload.bytes().length);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(payload.fileName()).build());

        return new ResponseEntity<>(payload.bytes(), headers, HttpStatus.OK);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
