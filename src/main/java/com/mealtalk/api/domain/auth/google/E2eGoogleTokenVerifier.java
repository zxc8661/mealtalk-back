package com.mealtalk.api.domain.auth.google;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fixed test identities for the {@code e2e} profile, in place of real Google
 * token verification.
 *
 * <p>There are two of them because owner isolation cannot be demonstrated with
 * one: proving that a photo is invisible to anyone but its owner requires a
 * second signed-in user to be refused. Any other token is still rejected.
 */
@Component
@Profile("e2e")
public class E2eGoogleTokenVerifier implements GoogleTokenVerifier {
    private static final Map<String, GoogleTokenPayload> FIXTURE_USERS = Map.of(
        "mealtalk-e2e-id-token",
        new GoogleTokenPayload("mealtalk-e2e-user", "e2e@mealtalk.test", "MealTalk E2E"),
        "mealtalk-e2e-id-token-2",
        new GoogleTokenPayload("mealtalk-e2e-user-2", "e2e-2@mealtalk.test", "MealTalk E2E Second")
    );

    @Override
    public GoogleTokenPayload verify(String idToken) {
        GoogleTokenPayload payload = FIXTURE_USERS.get(idToken);
        if (payload == null) {
            throw new InvalidGoogleTokenException("Invalid E2E ID token");
        }
        return payload;
    }
}
