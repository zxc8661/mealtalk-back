package com.mealtalk.api.auth;

import com.mealtalk.api.domain.auth.google.GoogleTokenPayload;
import com.mealtalk.api.domain.auth.google.GoogleTokenVerifier;
import com.mealtalk.api.domain.user.repository.UserProfileRepository;
import com.mealtalk.api.domain.user.repository.UserRepository;
import com.mealtalk.api.domain.user.repository.UserTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserTargetRepository userTargetRepository;
    @MockitoBean private GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    public void clearUsers() {
        userTargetRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void googleLoginCreatesUserAndIssuedTokenCanReadMe() throws Exception {
        when(googleTokenVerifier.verify("new-google-id-token"))
            .thenReturn(new GoogleTokenPayload("google-subject-1", "new@example.com", "새사용자"));

        String response = mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"new-google-id-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.profileCompleted").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String token = response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("new@example.com"))
            .andExpect(jsonPath("$.profileCompleted").value(false));

        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    public void googleLoginReusesExistingUserWhenSubjectMatches() throws Exception {
        GoogleTokenPayload payload = new GoogleTokenPayload("google-subject-2", "old@example.com", "기존사용자");
        when(googleTokenVerifier.verify("existing-google-id-token")).thenReturn(payload);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idToken\":\"existing-google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())));
        }

        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    public void meRejectsRequestWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void profileUpdateCompletesProfileAndReturnsTargetsFromMe() throws Exception {
        String token = login("profile-google-token", "profile-subject", "profile@example.com", "프로필사용자");

        mockMvc.perform(put("/api/v1/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "heightCm": 178.5,
                      "weightKg": 82.3,
                      "activityLevel": "MEDIUM",
                      "goalMode": "LOSS",
                      "targets": [
                        {"targetType": "DAILY_CALORIES", "targetValue": 2000},
                        {"targetType": "DAILY_PROTEIN", "targetValue": 150, "dueDate": "2026-12-31"}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileCompleted").value(true))
            .andExpect(jsonPath("$.profile.heightCm").value(178.5))
            .andExpect(jsonPath("$.profile.weightKg").value(82.3))
            .andExpect(jsonPath("$.profile.activityLevel").value("MEDIUM"))
            .andExpect(jsonPath("$.profile.goalMode").value("LOSS"))
            .andExpect(jsonPath("$.targets.length()").value(2));

        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileCompleted").value(true))
            .andExpect(jsonPath("$.profile.goalMode").value("LOSS"))
            .andExpect(jsonPath("$.targets[?(@.targetType == 'DAILY_CALORIES')].targetValue").value(2000.0))
            .andExpect(jsonPath("$.targets[?(@.targetType == 'DAILY_PROTEIN')].targetValue").value(150.0));

        assertThat(userProfileRepository.findAll()).hasSize(1);
        assertThat(userTargetRepository.findAll()).hasSize(2);
    }

    @Test
    public void profileUpdateReplacesExistingTargetOfSameType() throws Exception {
        String token = login("target-google-token", "target-subject", "target@example.com", "목표사용자");

        String firstRequest = """
            {
              "heightCm": 170,
              "weightKg": 70,
              "activityLevel": "LOW",
              "goalMode": "MAINTAIN",
              "targets": [
                {"targetType": "DAILY_CALORIES", "targetValue": 1900}
              ]
            }
            """;
        mockMvc.perform(put("/api/v1/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstRequest))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstRequest.replace("1900", "2100")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targets.length()").value(1))
            .andExpect(jsonPath("$.targets[0].targetValue").value(2100.0));

        assertThat(userTargetRepository.findAll()).hasSize(1);
    }

    @Test
    public void profileUpdateRejectsInvalidValues() throws Exception {
        String token = login("invalid-profile-token", "invalid-profile-subject", "invalid@example.com", "검증사용자");

        mockMvc.perform(put("/api/v1/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "heightCm": 0,
                      "weightKg": -1,
                      "activityLevel": "MEDIUM",
                      "goalMode": "LOSS",
                      "targets": [
                        {"targetType": "DAILY_CALORIES", "targetValue": 0}
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    private String login(String token, String subject, String email, String name) throws Exception {
        when(googleTokenVerifier.verify(token)).thenReturn(new GoogleTokenPayload(subject, email, name));
        String response = mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + token + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
