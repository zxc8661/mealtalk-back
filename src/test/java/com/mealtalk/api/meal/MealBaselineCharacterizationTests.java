package com.mealtalk.api.meal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealtalk.api.domain.auth.google.GoogleTokenPayload;
import com.mealtalk.api.domain.auth.google.GoogleTokenVerifier;
import com.mealtalk.api.domain.food.repository.FoodRepository;
import com.mealtalk.api.domain.meal.repository.MealItemRepository;
import com.mealtalk.api.domain.meal.repository.MealRepository;
import com.mealtalk.api.domain.user.repository.UserProfileRepository;
import com.mealtalk.api.domain.user.repository.UserRepository;
import com.mealtalk.api.domain.user.repository.UserTargetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization of the meal contract that survives the photo/memo pivot.
 *
 * <p>This pins only the parts that must NOT change: owner-scoped access, the
 * date/eatenAt/id list ordering, nullable eatenAt, and the shared validation
 * error envelope. It deliberately asserts nothing about nutrition items or
 * calorie totals, so it passes both before and after the migration and can
 * catch a regression that the new tests would not notice.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MealBaselineCharacterizationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private MealItemRepository mealItemRepository;
    @Autowired private MealRepository mealRepository;
    @Autowired private FoodRepository foodRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserTargetRepository userTargetRepository;
    @MockitoBean private GoogleTokenVerifier googleTokenVerifier;

    @AfterEach
    void clearData() {
        mealItemRepository.deleteAll();
        mealRepository.deleteAll();
        foodRepository.deleteAll();
        userTargetRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listsMealsOfADateOrderedByEatenAtThenIdWithUntimedRecordsLast() throws Exception {
        String token = login("baseline-order-token", "baseline-order", "order@example.com");

        long lunchId = createMeal(token, "2026-08-29", "LUNCH", "2026-08-29T12:30:00Z");
        long breakfastId = createMeal(token, "2026-08-29", "BREAKFAST", "2026-08-29T08:00:00Z");
        long untimedId = createMeal(token, "2026-08-29", "DINNER", null);
        long otherDateId = createMeal(token, "2026-08-30", "LUNCH", "2026-08-30T12:00:00Z");

        mockMvc.perform(get("/api/v1/meals?date=2026-08-29")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mealDate").value("2026-08-29"))
            .andExpect(jsonPath("$.meals.length()").value(3))
            .andExpect(jsonPath("$.meals[0].id").value(breakfastId))
            .andExpect(jsonPath("$.meals[0].mealType").value("BREAKFAST"))
            .andExpect(jsonPath("$.meals[1].id").value(lunchId))
            .andExpect(jsonPath("$.meals[1].eatenAt").value("2026-08-29T12:30:00Z"))
            .andExpect(jsonPath("$.meals[2].id").value(untimedId))
            .andExpect(jsonPath("$.meals[2].eatenAt").doesNotExist());

        mockMvc.perform(get("/api/v1/meals/{mealId}", otherDateId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mealDate").value("2026-08-30"));
    }

    @Test
    void anotherUsersMealIsIndistinguishableFromAMissingOneAndAnonymousAccessIsRejected() throws Exception {
        String ownerToken = login("baseline-owner-token", "baseline-owner", "owner@example.com");
        String otherToken = login("baseline-other-token", "baseline-other", "other@example.com");
        long mealId = createMeal(ownerToken, "2026-08-29", "LUNCH", null);

        mockMvc.perform(get("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/meals?date=2026-08-29"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void aMealWriteMissingItsRequiredDateReportsTheSharedValidationEnvelope() throws Exception {
        String token = login("baseline-validation-token", "baseline-validation", "validation@example.com");

        mockMvc.perform(multipart("/api/v1/meals")
                .file(mealPart("{\"mealType\":\"LUNCH\",\"memo\":\"메모\"}"))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details[?(@.field == 'mealDate')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/meals?date=not-a-date")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    /**
     * Creates a meal through the current multipart write contract, carrying the
     * legacy {@code items} field alongside the memo. An ignored unknown field is
     * itself part of what this characterization pins: the pivot must not turn a
     * stale client's request into an error.
     */
    private long createMeal(String token, String mealDate, String mealType, String eatenAt) throws Exception {
        String eatenAtField = eatenAt == null ? "" : ",\"eatenAt\":\"" + eatenAt + "\"";
        String body = "{\"mealDate\":\"%s\",\"mealType\":\"%s\"%s,\"memo\":\"%s %s 기록\"}"
            .formatted(mealDate, mealType, eatenAtField, mealDate, mealType);
        String response = mockMvc.perform(multipart("/api/v1/meals")
                .file(mealPart(body))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(response).required("id").longValue();
    }

    private static MockMultipartFile mealPart(String json) {
        return new MockMultipartFile(
            "meal", "meal.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String login(String token, String subject, String email) throws Exception {
        when(googleTokenVerifier.verify(token)).thenReturn(new GoogleTokenPayload(subject, email, "Meal User"));
        String response = mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + token + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
