package com.smartcertificate.template;

import jakarta.validation.constraints.Size;

public record DuplicateTemplateRequest(
        @Size(max = 180, message = "Template name must not exceed 180 characters.") String name) {}
