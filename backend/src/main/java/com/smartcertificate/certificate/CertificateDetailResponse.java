package com.smartcertificate.certificate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CertificateDetailResponse(
        UUID id,
        UUID organizationId,
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
        UUID revokedBy,
        String revokedReason,
        boolean pdfAvailable,
        boolean pngAvailable,
        Map<String, String> metadata,
        String idempotencyKey,
        UUID createdBy,
        Instant createdAt) {}
