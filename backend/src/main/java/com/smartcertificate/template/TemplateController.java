package com.smartcertificate.template;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Templates", description = "Template CRUD, versioning, schema validation, and lifecycle")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('template:read')")
    @Operation(summary = "Search and paginate organization templates")
    public ApiResponse<List<TemplateSummaryResponse>> listTemplates(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "status", required = false) TemplateStatus status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "orientation", required = false) TemplateOrientation orientation,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        UUID organizationId = SecurityContextUtils.requireOrganizationId();
        Page<TemplateSummaryResponse> result = templateService.listTemplates(
                organizationId,
                query,
                status,
                category,
                orientation,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return ApiResponse.ok(
                result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('template:write')")
    @Operation(summary = "Create a new draft certificate template")
    public ResponseEntity<ApiResponse<TemplateDetailResponse>> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        TemplateDetailResponse created =
                templateService.createDraft(principal.getOrganizationId(), principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('template:read')")
    @Operation(summary = "Get template details, latest version design JSON, and version history")
    public ApiResponse<TemplateDetailResponse> getTemplate(@PathVariable("id") UUID templateId) {
        return ApiResponse.ok(templateService.getTemplate(SecurityContextUtils.requireOrganizationId(), templateId));
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    @PreAuthorize("hasAuthority('template:read')")
    @Operation(summary = "Get a specific immutable template version")
    public ApiResponse<TemplateVersionResponse> getTemplateVersion(
            @PathVariable("id") UUID templateId, @PathVariable("versionNumber") int versionNumber) {
        return ApiResponse.ok(templateService.getTemplateVersion(
                SecurityContextUtils.requireOrganizationId(), templateId, versionNumber));
    }

    @PutMapping("/{id}/draft")
    @PreAuthorize("hasAuthority('template:write')")
    @Operation(summary = "Update template draft or create a new draft version if published")
    public ApiResponse<TemplateDetailResponse> updateDraft(
            @PathVariable("id") UUID templateId, @Valid @RequestBody UpdateTemplateDraftRequest request) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        return ApiResponse.ok(
                templateService.updateDraft(principal.getOrganizationId(), principal.getUserId(), templateId, request));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAuthority('template:write')")
    @Operation(summary = "Duplicate an existing template as a new draft")
    public ResponseEntity<ApiResponse<TemplateDetailResponse>> duplicateTemplate(
            @PathVariable("id") UUID templateId,
            @Valid @RequestBody(required = false) DuplicateTemplateRequest request) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        TemplateDetailResponse duplicated = templateService.duplicateTemplate(
                principal.getOrganizationId(), principal.getUserId(), templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(duplicated));
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('template:read')")
    @Operation(summary = "Validate and render a placeholder-resolved template preview")
    public ApiResponse<TemplatePreviewResponse> previewTemplate(
            @PathVariable("id") UUID templateId, @RequestBody(required = false) PreviewTemplateRequest request) {
        return ApiResponse.ok(
                templateService.previewTemplate(SecurityContextUtils.requireOrganizationId(), templateId, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('template:publish')")
    @Operation(summary = "Publish the current template version")
    public ApiResponse<TemplateDetailResponse> publishTemplate(@PathVariable("id") UUID templateId) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        return ApiResponse.ok(
                templateService.publishTemplate(principal.getOrganizationId(), principal.getUserId(), templateId));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('template:publish')")
    @Operation(summary = "Archive a template")
    public ApiResponse<TemplateDetailResponse> archiveTemplate(@PathVariable("id") UUID templateId) {
        AuthenticatedPrincipal principal = SecurityContextUtils.requirePrincipal();
        return ApiResponse.ok(
                templateService.archiveTemplate(principal.getOrganizationId(), principal.getUserId(), templateId));
    }
}
