package com.smartcertificate.certificate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record IssueCertificateRequest(
        @NotNull(message = "Template ID is required.") UUID templateId,
        @NotBlank(message = "Recipient name is required.")
                @Size(max = 200, message = "Recipient name must not exceed 200 characters.")
                String recipientName,
        @Email(message = "Recipient email must be a valid email address.")
                @Size(max = 254, message = "Recipient email must not exceed 254 characters.")
                String recipientEmail,
        @NotBlank(message = "Course or program name is required.")
                @Size(max = 200, message = "Course name must not exceed 200 characters.")
                String courseName,
        LocalDate completionDate,
        Instant expiresAt,
        Map<String, String> metadata,
        @Size(max = 160, message = "Idempotency key must not exceed 160 characters.") String idempotencyKey) {}
