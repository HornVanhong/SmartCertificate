package com.smartcertificate.certificate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CertificateSummaryResponse(
        UUID id,
        UUID templateId,
        UUID templateVersionId,
        String recipientName,
        String recipientEmail,
        String courseName,
        String certificateNumber,
        CertificateStatus status,
        LocalDate completionDate,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        UUID createdBy,
        Instant createdAt) {}
