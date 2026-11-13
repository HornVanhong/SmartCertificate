package com.smartcertificate.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record SecurityProperties(
        String jwtSecret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds,
        long passwordResetTtlSeconds,
        long invitationTtlSeconds,
        boolean cookieSecure,
        boolean selfRegistrationEnabled) {}
