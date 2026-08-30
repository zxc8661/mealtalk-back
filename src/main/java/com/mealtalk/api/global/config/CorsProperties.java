package com.mealtalk.api.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mealtalk.cors")
public record CorsProperties(List<String> allowedOrigins) {
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowedOrigins.stream().anyMatch("*"::equals)) {
            throw new IllegalArgumentException("CORS allowed origins must not contain a wildcard");
        }
    }
}
