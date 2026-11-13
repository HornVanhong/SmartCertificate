package com.smartcertificate.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 1, max = 160, message = "Full name must be between 1 and 160 characters.") String fullName,
        @Pattern(regexp = "^(ADMIN|GENERAL_USER)$", message = "Role must be ADMIN or GENERAL_USER.") String roleName) {}
