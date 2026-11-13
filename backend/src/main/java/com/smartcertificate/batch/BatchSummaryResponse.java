package com.smartcertificate.batch;

import java.time.Instant;
import java.util.UUID;

public record BatchSummaryResponse(
        UUID id,
        UUID templateId,
        UUID templateVersionId,
        String sourceFileName,
        BatchStatus status,
        int totalRecords,
        int validRecords,
        int invalidRecords,
        int successfulRecords,
        int failedRecords,
        boolean zipAvailable,
        UUID createdBy,
        Instant createdAt,
        Instant completedAt) {}
