package com.mealtalk.api.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mealtalk.auth")
public record AuthProperties(
    String googleClientId,
    String jwtSecret,
    Duration accessTokenTtl
) {
    public AuthProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = "dev-only-mealtalk-jwt-secret-change-before-production";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofHours(2);
        }
    }
}
