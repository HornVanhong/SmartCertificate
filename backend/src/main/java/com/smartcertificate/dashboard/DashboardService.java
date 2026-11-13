package com.smartcertificate.dashboard;

import com.smartcertificate.audit.AuditEventService;
import java.util.List;
import java.util.UUID;

public interface DashboardService {

    DashboardSummaryResponse getSummary(UUID organizationId, UUID actorUserId, boolean canReadAll);

    List<IssuanceTrendPointResponse> getIssuanceTrends(
            UUID organizationId, UUID actorUserId, boolean canReadAll, int days);

    List<AuditEventService.AuditLogResponse> getRecentActivity(
            UUID organizationId, UUID actorUserId, boolean canReadAllAudit, int limit);
}
