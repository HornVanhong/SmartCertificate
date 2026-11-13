package com.smartcertificate.batch;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BatchDetailResponse(
        UUID id,
        UUID organizationId,
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
        List<String> sourceHeaders,
        Map<String, String> columnMapping,
        List<String> templatePlaceholders,
        List<Map<String, String>> sampleRows,
        String idempotencyKey,
        String errorSummary,
        UUID createdBy,
        Instant createdAt,
        Instant completedAt) {}
