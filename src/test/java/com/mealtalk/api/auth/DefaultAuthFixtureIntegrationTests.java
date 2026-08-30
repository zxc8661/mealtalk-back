package com.mealtalk.api.auth;

import com.mealtalk.api.domain.auth.google.GoogleIdTokenAdapter;
import com.mealtalk.api.domain.auth.google.GoogleTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DefaultAuthFixtureIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private GoogleTokenVerifier googleTokenVerifier;

    @Test
    public void defaultProfileUsesGoogleVerifierAndRejectsE2eFixtureToken() throws Exception {
        assertThat(googleTokenVerifier).isInstanceOf(GoogleIdTokenAdapter.class);

        mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"mealtalk-e2e-id-token\"}"))
            .andExpect(status().isUnauthorized());
    }
}
