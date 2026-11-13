package com.smartcertificate.certificate;

import com.smartcertificate.audit.AuditEventService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CertificateService {

    record IssueOutcome(CertificateDetailResponse certificate, boolean idempotentReplay) {}

    record DownloadPayload(String fileName, String contentType, byte[] bytes) {}

    record PublicCertificateSnapshot(
            UUID certificateId,
            UUID organizationId,
            CertificateStatus status,
            String certificateNumber,
            String recipientName,
            String courseName,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt) {}

    record CertificatePdfPayload(String certificateNumber, String recipientName, byte[] pdfBytes) {}

    record CertificateStats(
            long totalCertificates, long issuedCertificates, long revokedCertificates, long issuedLast30Days) {}

    record DailyIssuanceCount(LocalDate date, long issuedCount) {}

    Page<CertificateSummaryResponse> listCertificates(
            UUID organizationId,
            UUID actorUserId,
            boolean canReadAll,
            String query,
            CertificateStatus status,
            UUID templateId,
            Pageable pageable);

    IssueOutcome issueCertificate(UUID organizationId, UUID actorUserId, IssueCertificateRequest request);

    CertificateDetailResponse getCertificate(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID certificateId);

    DownloadPayload downloadCertificate(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID certificateId, CertificateFormat format);

    CertificateDetailResponse revokeCertificate(
            UUID organizationId, UUID actorUserId, UUID certificateId, RevokeCertificateRequest request);

    Page<AuditEventService.AuditLogResponse> listCertificateAuditLogs(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID certificateId, Pageable pageable);

    Optional<PublicCertificateSnapshot> findPublicSnapshotByTokenHash(String tokenHash);

    Optional<CertificatePdfPayload> loadCertificatePdfForBatch(UUID organizationId, UUID certificateId);

    CertificateStats getCertificateStats(UUID organizationId, UUID actorUserId, boolean canReadAll);

    List<DailyIssuanceCount> getDailyIssuanceTrend(UUID organizationId, UUID actorUserId, boolean canReadAll, int days);
}
