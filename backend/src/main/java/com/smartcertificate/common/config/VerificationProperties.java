package com.smartcertificate.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(String publicBaseUrl, Integer rateLimitPerMinute) {

    public VerificationProperties {
        if (rateLimitPerMinute == null || rateLimitPerMinute <= 0) {
            rateLimitPerMinute = 60;
        }
    }
}
