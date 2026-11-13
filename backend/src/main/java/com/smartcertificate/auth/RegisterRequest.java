package com.smartcertificate.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required.")
                @Size(max = 160, message = "Full name must not exceed 160 characters.")
                String fullName,
        @NotBlank(message = "Email is required.")
                @Email(message = "Email must be valid.")
                @Size(max = 254, message = "Email must not exceed 254 characters.")
                String email,
        @NotBlank(message = "Password is required.")
                @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters.")
                String password,
        @NotBlank(message = "Organization name is required.")
                @Size(max = 200, message = "Organization name must not exceed 200 characters.")
                String organizationName) {}
