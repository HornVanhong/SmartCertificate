package com.smartcertificate.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record UpdateOrganizationRequest(
        @NotBlank(message = "Organization name is required.")
                @Size(max = 200, message = "Organization name must not exceed 200 characters.")
                String name,
        UUID logoAssetId,
        @Email(message = "Contact email must be a valid email address.")
                @Size(max = 254, message = "Contact email must not exceed 254 characters.")
                String contactEmail,
        @Size(max = 50, message = "Phone must not exceed 50 characters.") String phone,
        @Size(max = 500, message = "Address must not exceed 500 characters.") String address,
        @Pattern(regexp = "^$|^https?://.*", message = "Website must start with http:// or https://.")
                @Size(max = 300, message = "Website URL must not exceed 300 characters.")
                String website,
        Map<String, Object> brandSettings) {}
