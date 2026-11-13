package com.smartcertificate.template;

import java.time.Instant;
import java.util.UUID;

public record TemplateVersionResponse(
        UUID id, int versionNumber, DesignDocument designJson, UUID createdBy, Instant createdAt) {}
