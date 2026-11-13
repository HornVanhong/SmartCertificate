package com.smartcertificate.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditEventService {

    record AuditLogResponse(
            UUID id,
            UUID certificateId,
            UUID actorUserId,
            String action,
            String reason,
            Map<String, Object> details,
            Instant createdAt) {}

    void recordEvent(UUID organizationId, UUID actorUserId, String action, String reason, Map<String, Object> details);

    void recordCertificateEvent(
            UUID organizationId,
            UUID certificateId,
            UUID actorUserId,
            String action,
            String reason,
            Map<String, Object> details);

    Page<AuditLogResponse> listCertificateAuditLogs(UUID organizationId, UUID certificateId, Pageable pageable);

    Page<AuditLogResponse> listOrganizationAuditLogs(
            UUID organizationId, UUID actorUserIdFilter, String action, String query, Pageable pageable);
}
