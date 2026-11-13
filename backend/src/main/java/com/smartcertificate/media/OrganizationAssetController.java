package com.smartcertificate.media;

import com.smartcertificate.common.api.ApiResponse;
import com.smartcertificate.common.api.PageMeta;
import com.smartcertificate.common.security.AuthenticatedPrincipal;
import com.smartcertificate.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizations/current/assets")
@Tag(name = "Organization Assets", description = "Validated upload and listing of organization assets")
public class OrganizationAssetController {

    private final MediaAssetService mediaAssetService;

    public OrganizationAssetController(MediaAssetService mediaAssetService) {
        this.mediaAssetService = mediaAssetService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('org:assets:upload')")
    @Operation(summary = "Upload logo, signature, stamp, or general image asset")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> uploadAsset(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "assetType", required = false, defaultValue = "GENERAL") AssetType assetType) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        MediaAssetResponse response = mediaAssetService.uploadOrganizationAsset(
                principal.getOrganizationId(), principal.getUserId(), assetType, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('org:read')")
    @Operation(summary = "List organization media assets")
    public ApiResponse<List<MediaAssetResponse>> listAssets(
            @RequestParam(value = "assetType", required = false) AssetType assetType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        UUID organizationId = SecurityContextUtils.requireOrganizationId();
        Page<MediaAssetResponse> result =
                mediaAssetService.listOrganizationAssets(organizationId, assetType, PageRequest.of(safePage, safeSize));
        return ApiResponse.ok(
                result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download private asset content via signed URL or authorized session")
    public ResponseEntity<byte[]> getAssetContent(
            @PathVariable("id") UUID assetId,
            @RequestParam(value = "expires", required = false) Long expires,
            @RequestParam(value = "sig", required = false) String sig) {
        UUID authenticatedOrgId = SecurityContextUtils.currentPrincipal()
                .map(AuthenticatedPrincipal::getOrganizationId)
                .orElse(null);

        MediaAssetService.AssetContent content =
                mediaAssetService.loadSignedOrAuthorizedContent(assetId, authenticatedOrgId, expires, sig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.mimeType()));
        boolean isImage = content.mimeType() != null && content.mimeType().startsWith("image/");
        headers.setContentDisposition((isImage ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(content.fileName())
                .build());
        headers.setCacheControl(CacheControl.noStore());
        headers.add("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(content.bytes(), headers, HttpStatus.OK);
    }
}
