package com.smartcertificate.user;

import java.time.Instant;
import java.util.UUID;

public record UserMembershipResponse(
        UUID membershipId,
        UUID userId,
        String fullName,
        String email,
        String userStatus,
        String membershipStatus,
        String roleName,
        Instant createdAt) {}
