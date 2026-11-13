package com.smartcertificate.batch;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record BatchColumnMappingRequest(
        @NotNull(message = "Column mappings are required.") Map<String, String> mappings) {}
