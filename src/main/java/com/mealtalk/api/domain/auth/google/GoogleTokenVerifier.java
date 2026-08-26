package com.mealtalk.api.domain.auth.google;

public interface GoogleTokenVerifier {
    GoogleTokenPayload verify(String idToken);
}
