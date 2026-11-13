package com.smartcertificate.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @NotBlank(message = "Email is required.")
                @Email(message = "Email must be valid.")
                @Size(max = 254, message = "Email must not exceed 254 characters.")
                String email,
        @NotBlank(message = "Full name is required.")
                @Size(max = 160, message = "Full name must not exceed 160 characters.")
                String fullName,
        @NotBlank(message = "Role name is required.")
                @Pattern(regexp = "^(ADMIN|GENERAL_USER)$", message = "Role must be ADMIN or GENERAL_USER.")
                String roleName) {}
