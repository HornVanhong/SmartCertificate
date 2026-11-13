package com.smartcertificate.user;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id, String email, String fullName, String roleName, String status, Instant expiresAt, Instant createdAt) {}
