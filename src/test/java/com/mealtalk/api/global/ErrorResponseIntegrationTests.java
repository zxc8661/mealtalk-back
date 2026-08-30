package com.mealtalk.api.global;

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

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks the error envelope every client reads. These assert the response body,
 * which the other suites deliberately leave to status codes alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ErrorResponseIntegrationTests {
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

    private String signIn() throws Exception {
        when(googleTokenVerifier.verify("error-envelope-token"))
            .thenReturn(new GoogleTokenPayload("error-subject", "error@example.com", "에러"));
        String response = mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"error-envelope-token\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    @Test
    public void unauthenticatedRequestReturnsJsonBodyNotEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message", not(blankOrNullString())))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    public void bodyValidationFailureReportsOffendingFields() throws Exception {
        String token = signIn();

        mockMvc.perform(post("/api/v1/meals")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mealDate\":\"2026-03-14\",\"mealType\":\"LUNCH\",\"eatenAt\":null,\"items\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", not(blankOrNullString())))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details").isArray())
            .andExpect(jsonPath("$.details[0].field", not(blankOrNullString())))
            .andExpect(jsonPath("$.details[0].message", not(blankOrNullString())));
    }

    @Test
    public void missingRequiredQueryParameterIsReported() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/api/v1/meals").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details[0].field").value("date"));
    }

    @Test
    public void malformedQueryParameterIsReported() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/api/v1/meals?date=not-a-date").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.message", not(blankOrNullString())));
    }

    @Test
    public void unreadableJsonBodyIsReported() throws Exception {
        String token = signIn();

        mockMvc.perform(post("/api/v1/meals")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json }"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    public void missingResourceReturnsNotFoundEnvelope() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/api/v1/meals/999999").header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            // Internal English reasons such as "Meal not found" must not reach clients.
            .andExpect(jsonPath("$.message").value("요청한 정보를 찾을 수 없습니다."));
    }

    /** An unmapped but permitted path must be a 404, never a 500 from the catch-all. */
    @Test
    public void unmappedPathReturnsNotFoundNotServerError() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    public void successfulResponseCarriesNoErrorFields() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/api/v1/meals?date=2026-03-14").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.message").doesNotExist());
    }
}
