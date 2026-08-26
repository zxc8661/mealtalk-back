package com.mealtalk.api.domain.auth.dto;

public record AuthTokenResponse(
    String accessToken,
    String tokenType,
    boolean profileCompleted
) {
    public static AuthTokenResponse bearer(String accessToken, boolean profileCompleted) {
        return new AuthTokenResponse(accessToken, "Bearer", profileCompleted);
    }
}
