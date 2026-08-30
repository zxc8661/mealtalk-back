package com.mealtalk.api.domain.auth.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.mealtalk.api.domain.auth.config.AuthProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
@Profile("!e2e")
public class GoogleIdTokenAdapter implements com.mealtalk.api.domain.auth.google.GoogleTokenVerifier {
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenAdapter(AuthProperties properties) throws GeneralSecurityException, IOException {
        this.verifier = new GoogleIdTokenVerifier.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance()
        )
            .setAudience(List.of(properties.googleClientId()))
            .build();
    }

    @Override
    public GoogleTokenPayload verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new InvalidGoogleTokenException("Invalid Google ID token");
            }
            GoogleIdToken.Payload payload = token.getPayload();
            String subject = payload.getSubject();
            String email = payload.getEmail();
            if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
                throw new InvalidGoogleTokenException("Google ID token missing required subject or email");
            }
            return new GoogleTokenPayload(subject, email, (String) payload.get("name"));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new InvalidGoogleTokenException("Google ID token verification failed", e);
        }
    }
}
