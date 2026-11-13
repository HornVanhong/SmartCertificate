package com.smartcertificate.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank(message = "Invitation token is required.") String token,
        @Size(max = 160, message = "Full name must not exceed 160 characters.") String fullName,
        @NotBlank(message = "Password is required.")
                @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters.")
                String password) {}
