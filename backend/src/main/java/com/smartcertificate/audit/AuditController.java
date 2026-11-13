package com.smartcertificate.audit;

import com.smartcertificate.common.api.ApiResponse;
import com.smartcertificate.common.api.PageMeta;
import com.smartcertificate.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit Logs", description = "Organization-scoped read-only audit event stream")
public class AuditController {

    private final AuditEventService auditEventService;

    public AuditController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "List organization audit logs with optional action and text filters")
    public ApiResponse<List<AuditEventService.AuditLogResponse>> listAuditLogs(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        UUID organizationId = SecurityContextUtils.requireOrganizationId();
        Page<AuditEventService.AuditLogResponse> result = auditEventService.listOrganizationAuditLogs(
                organizationId,
                null,
                action,
                query,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));

        return ApiResponse.ok(
                result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }
}
