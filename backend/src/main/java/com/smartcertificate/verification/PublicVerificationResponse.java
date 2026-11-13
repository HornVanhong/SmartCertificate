package com.smartcertificate.verification;

import java.time.Instant;

public record PublicVerificationResponse(
        PublicVerificationStatus status,
        String certificateNumber,
        String recipientName,
        String courseName,
        String organizationName,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt) {}
