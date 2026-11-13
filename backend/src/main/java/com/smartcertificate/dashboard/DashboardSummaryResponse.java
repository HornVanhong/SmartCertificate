package com.smartcertificate.dashboard;

public record DashboardSummaryResponse(
        long totalCertificates,
        long issuedCertificates,
        long revokedCertificates,
        long issuedLast30Days,
        long totalTemplates,
        long publishedTemplates,
        long draftTemplates,
        long totalBatches,
        long activeBatches,
        String scope) {}
