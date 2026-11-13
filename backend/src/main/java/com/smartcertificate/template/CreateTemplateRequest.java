package com.smartcertificate.template;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateTemplateRequest(
        @NotBlank(message = "Template name is required.")
                @Size(max = 180, message = "Template name must not exceed 180 characters.")
                String name,
        @Size(max = 1000, message = "Description must not exceed 1000 characters.") String description,
        @Size(max = 80, message = "Category must not exceed 80 characters.") String category,
        @NotNull(message = "Orientation is required.") TemplateOrientation orientation,
        @DecimalMin(value = "100.0", message = "Page width must be at least 100.")
                @DecimalMax(value = "5000.0", message = "Page width must not exceed 5000.")
                BigDecimal pageWidth,
        @DecimalMin(value = "100.0", message = "Page height must be at least 100.")
                @DecimalMax(value = "5000.0", message = "Page height must not exceed 5000.")
                BigDecimal pageHeight,
        DesignDocument designJson) {}
