package com.mealtalk.api.domain.auth.google;

public record GoogleTokenPayload(String subject, String email, String name) {
}
