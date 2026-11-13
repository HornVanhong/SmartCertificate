package com.smartcertificate.auth;

import java.util.Set;
import java.util.UUID;

public record AuthMeResponse(
        UUID userId,
        String fullName,
        String email,
        OrganizationSummaryDto organization,
        String roleName,
        Set<String> permissions) {

    public record OrganizationSummaryDto(UUID id, String name, String slug) {}
}
