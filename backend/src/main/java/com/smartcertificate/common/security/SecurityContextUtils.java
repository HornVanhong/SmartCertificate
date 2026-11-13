package com.smartcertificate.common.security;

import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextUtils {

    private SecurityContextUtils() {}

    public static Optional<AuthenticatedPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static AuthenticatedPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Authentication is required."));
    }

    public static UUID requireOrganizationId() {
        return requirePrincipal().getOrganizationId();
    }

    public static UUID requireUserId() {
        return requirePrincipal().getUserId();
    }
}
