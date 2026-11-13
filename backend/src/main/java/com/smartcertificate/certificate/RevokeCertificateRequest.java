package com.smartcertificate.certificate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeCertificateRequest(
        @NotBlank(message = "Revocation reason is required.")
                @Size(max = 1000, message = "Revocation reason must not exceed 1000 characters.")
                String reason) {}
