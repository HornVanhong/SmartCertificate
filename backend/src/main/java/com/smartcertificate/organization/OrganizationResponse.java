package com.smartcertificate.organization;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        UUID logoAssetId,
        String logoUrl,
        String contactEmail,
        String phone,
        String address,
        String website,
        Map<String, Object> brandSettings,
        Instant createdAt,
        Instant updatedAt) {}
