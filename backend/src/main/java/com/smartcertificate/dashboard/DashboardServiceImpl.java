package com.smartcertificate.dashboard;

import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.batch.CertificateBatchService;
import com.smartcertificate.certificate.CertificateService;
import com.smartcertificate.template.TemplateService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DashboardServiceImpl implements DashboardService {

    private final CertificateService certificateService;
    private final TemplateService templateService;
    private final CertificateBatchService batchService;
    private final AuditEventService auditEventService;

    DashboardServiceImpl(
            CertificateService certificateService,
            TemplateService templateService,
            CertificateBatchService batchService,
            AuditEventService auditEventService) {
        this.certificateService = certificateService;
        this.templateService = templateService;
        this.batchService = batchService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(UUID organizationId, UUID actorUserId, boolean canReadAll) {
        CertificateService.CertificateStats certStats =
                certificateService.getCertificateStats(organizationId, actorUserId, canReadAll);
        TemplateService.TemplateStats templateStats = templateService.getTemplateStats(organizationId);
        CertificateBatchService.BatchStats batchStats =
                batchService.getBatchStats(organizationId, actorUserId, canReadAll);

        return new DashboardSummaryResponse(
                certStats.totalCertificates(),
                certStats.issuedCertificates(),
                certStats.revokedCertificates(),
                certStats.issuedLast30Days(),
                templateStats.totalTemplates(),
                templateStats.publishedTemplates(),
                templateStats.draftTemplates(),
                batchStats.totalBatches(),
                batchStats.activeBatches(),
                canReadAll ? "ORGANIZATION" : "PERSONAL");
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssuanceTrendPointResponse> getIssuanceTrends(
            UUID organizationId, UUID actorUserId, boolean canReadAll, int days) {
        return certificateService.getDailyIssuanceTrend(organizationId, actorUserId, canReadAll, days).stream()
                .map(point -> new IssuanceTrendPointResponse(point.date(), point.issuedCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventService.AuditLogResponse> getRecentActivity(
            UUID organizationId, UUID actorUserId, boolean canReadAllAudit, int limit) {
        int clampedLimit = Math.min(Math.max(limit, 1), 30);
        UUID actorFilter = canReadAllAudit ? null : actorUserId;
        return auditEventService
                .listOrganizationAuditLogs(organizationId, actorFilter, null, null, PageRequest.of(0, clampedLimit))
                .getContent();
    }
}
