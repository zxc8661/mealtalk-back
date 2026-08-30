package com.mealtalk.api.domain.auth.google;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("e2e")
public class E2eGoogleTokenVerifier implements GoogleTokenVerifier {
    private static final String FIXTURE_TOKEN = "mealtalk-e2e-id-token";
    private static final GoogleTokenPayload FIXTURE_USER = new GoogleTokenPayload(
        "mealtalk-e2e-user",
        "e2e@mealtalk.test",
        "MealTalk E2E"
    );

    @Override
    public GoogleTokenPayload verify(String idToken) {
        if (!FIXTURE_TOKEN.equals(idToken)) {
            throw new InvalidGoogleTokenException("Invalid E2E ID token");
        }
        return FIXTURE_USER;
    }
}
