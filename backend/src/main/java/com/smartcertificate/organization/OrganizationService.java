package com.smartcertificate.organization;

import java.util.UUID;

public interface OrganizationService {

    record OrganizationSummary(UUID id, String name, String slug) {}

    OrganizationResponse getCurrentOrganization(UUID organizationId);

    OrganizationResponse updateCurrentOrganization(
            UUID organizationId, UUID actorUserId, UpdateOrganizationRequest request);

    OrganizationSummary createOrganization(String name, String contactEmail);

    OrganizationSummary getSummary(UUID organizationId);
}
