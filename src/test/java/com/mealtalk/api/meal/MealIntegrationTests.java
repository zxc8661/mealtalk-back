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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The meal record contract after the photo/memo pivot: a record is a date, a
 * meal type, an optional eaten time and a memo (and optionally a photo), never a
 * list of nutrition items with fabricated totals.
 *
 * <p>Writes go through the multipart contract - a required {@code meal} JSON
 * part plus an optional {@code photo} part - which replaced the JSON-only body.
 * These tests cover the memo side of it; the photo side lives in
 * {@code MealPhotoApiIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MealIntegrationTests {
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
    void ownerCreatesReadsUpdatesAndDeletesAMemoOnlyRecord() throws Exception {
        String token = login("memo-owner-token", "memo-owner", "memo-owner@example.com");

        long mealId = createMealId(token, payload("2026-08-29", "LUNCH", "2026-08-29T12:30:00Z", "\"  김치찌개랑 계란말이  \""));

        mockMvc.perform(get("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(mealId))
            .andExpect(jsonPath("$.mealDate").value("2026-08-29"))
            .andExpect(jsonPath("$.mealType").value("LUNCH"))
            .andExpect(jsonPath("$.eatenAt").value("2026-08-29T12:30:00Z"))
            .andExpect(jsonPath("$.memo").value("김치찌개랑 계란말이"));

        mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart(payload("2026-08-30", "DINNER", null, "\"저녁은 국수\"", "KEEP"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(mealId))
            .andExpect(jsonPath("$.mealDate").value("2026-08-30"))
            .andExpect(jsonPath("$.mealType").value("DINNER"))
            .andExpect(jsonPath("$.eatenAt").doesNotExist())
            .andExpect(jsonPath("$.memo").value("저녁은 국수"));

        mockMvc.perform(get("/api/v1/meals?date=2026-08-29")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meals.length()").value(0));

        mockMvc.perform(delete("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void recordResponsesCarryNoCalorieOrMacroTotals() throws Exception {
        String token = login("no-nutrition-token", "no-nutrition", "no-nutrition@example.com");
        long mealId = createMealId(token, payload("2026-08-29", "BREAKFAST", null, "\"토스트 한 조각\""));

        mockMvc.perform(get("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCaloriesKcal").doesNotExist())
            .andExpect(jsonPath("$.totalCarbohydratesG").doesNotExist())
            .andExpect(jsonPath("$.totalProteinG").doesNotExist())
            .andExpect(jsonPath("$.totalFatG").doesNotExist())
            .andExpect(jsonPath("$.items").doesNotExist());

        mockMvc.perform(get("/api/v1/meals?date=2026-08-29")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meals.length()").value(1))
            .andExpect(jsonPath("$.totalCaloriesKcal").doesNotExist())
            .andExpect(jsonPath("$.totalCarbohydratesG").doesNotExist())
            .andExpect(jsonPath("$.totalProteinG").doesNotExist())
            .andExpect(jsonPath("$.totalFatG").doesNotExist())
            .andExpect(jsonPath("$.meals[0].totalCaloriesKcal").doesNotExist())
            .andExpect(jsonPath("$.meals[0].items").doesNotExist());
    }

    @Test
    void anEmptyRecordIsRejectedWithTheSharedValidationEnvelope() throws Exception {
        String token = login("empty-record-token", "empty-record", "empty@example.com");

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(payload("2026-08-29", "LUNCH", null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(payload("2026-08-29", "LUNCH", null, "\"   \\n \\t  \""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(payload("2026-08-29", "LUNCH", null, "null"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(mealRepository.findAllByUserIdAndMealDateOrdered(
            userIdOf("empty-record"),
            java.time.LocalDate.of(2026, 8, 29)
        )).isEmpty();
    }

    @Test
    void memoIsAcceptedAtOneThousandCharactersAndRejectedBeyondIt() throws Exception {
        String token = login("memo-length-token", "memo-length", "length@example.com");

        String atLimit = "가".repeat(1000);
        long mealId = createMealId(token, payload("2026-08-29", "LUNCH", null, "\"" + atLimit + "\""));
        mockMvc.perform(get("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memo").value(atLimit));

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(payload("2026-08-29", "LUNCH", null, "\"" + "가".repeat(1001) + "\""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details[?(@.field == 'memo')]").isNotEmpty());

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(payload("2026-08-29", "LUNCH", null, "\"  " + "나".repeat(1000) + "  \""))))
            .andExpect(status().isCreated());
    }

    @Test
    void memoPreservesInnerNewlinesEmojiAndKoreanText() throws Exception {
        String token = login("memo-unicode-token", "memo-unicode", "unicode@example.com");
        String memo = "아침\n두 번째 줄 🍚🥢\n마지막 줄";

        long mealId = createMealId(token, payload("2026-08-29", "BREAKFAST", null,
            "\"  아침\\n두 번째 줄 \uD83C\uDF5A\uD83E\uDD62\\n마지막 줄  \""));

        mockMvc.perform(get("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memo").value(memo));
    }

    @Test
    void listKeepsEatenAtThenIdOrderingAndStaysOwnerScoped() throws Exception {
        String ownerToken = login("list-owner-token", "list-owner", "list-owner@example.com");
        String otherToken = login("list-other-token", "list-other", "list-other@example.com");

        long lunchId = createMealId(ownerToken, payload("2026-08-29", "LUNCH", "2026-08-29T12:30:00Z", "\"점심\""));
        long breakfastId = createMealId(ownerToken, payload("2026-08-29", "BREAKFAST", "2026-08-29T08:00:00Z", "\"아침\""));
        long untimedId = createMealId(ownerToken, payload("2026-08-29", "DINNER", null, "\"저녁\""));
        createMealId(otherToken, payload("2026-08-29", "LUNCH", "2026-08-29T11:00:00Z", "\"남의 점심\""));

        mockMvc.perform(get("/api/v1/meals?date=2026-08-29")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mealDate").value("2026-08-29"))
            .andExpect(jsonPath("$.meals.length()").value(3))
            .andExpect(jsonPath("$.meals[0].id").value(breakfastId))
            .andExpect(jsonPath("$.meals[1].id").value(lunchId))
            .andExpect(jsonPath("$.meals[2].id").value(untimedId))
            .andExpect(jsonPath("$.meals[2].eatenAt").doesNotExist());

        mockMvc.perform(get("/api/v1/meals/{mealId}", lunchId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(multipartUpdate(otherToken, lunchId)
                .file(mealPart(payload("2026-08-30", "DINNER", null, "\"덮어쓰기 시도\"", "KEEP"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/meals/{mealId}", lunchId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/meals/{mealId}", lunchId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memo").value("점심"));
    }

    @Test
    void anUpdateMayNotEmptyAnExistingRecord() throws Exception {
        String token = login("update-empty-token", "update-empty", "update-empty@example.com");
        long mealId = createMealId(token, payload("2026-08-29", "LUNCH", null, "\"원래 메모\""));

        mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart(payload("2026-08-29", "LUNCH", null, "\"    \"", "KEEP"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memo").value("원래 메모"));
    }

    @Test
    void anonymousAccessAndMalformedDateStillFail() throws Exception {
        mockMvc.perform(get("/api/v1/meals?date=2026-08-29"))
            .andExpect(status().isUnauthorized());

        String token = login("malformed-token", "malformed", "malformed@example.com");
        mockMvc.perform(get("/api/v1/meals?date=not-a-date")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private long userIdOf(String providerUserId) {
        return userRepository
            .findByProviderAndProviderUserId(com.mealtalk.api.domain.user.entity.AuthProvider.GOOGLE, providerUserId)
            .orElseThrow()
            .getId();
    }

    private long createMealId(String token, String payload) throws Exception {
        String response = mockMvc.perform(multipartCreate(token).file(mealPart(payload)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(response).required("id").longValue();
    }

    private static MockMultipartHttpServletRequestBuilder multipartCreate(String token) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/meals");
        builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private static MockMultipartHttpServletRequestBuilder multipartUpdate(String token, long mealId) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/meals/{mealId}", mealId);
        builder.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private static MockMultipartFile mealPart(String json) {
        return new MockMultipartFile(
            "meal", "meal.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)
        );
    }

    /** {@code memoJson} is raw JSON: a quoted string, {@code null}, or absent. */
    private String payload(String mealDate, String mealType, String eatenAt, String memoJson) {
        return payload(mealDate, mealType, eatenAt, memoJson, null);
    }

    private String payload(String mealDate, String mealType, String eatenAt, String memoJson, String photoAction) {
        String eatenAtField = eatenAt == null ? "" : ",\"eatenAt\":\"" + eatenAt + "\"";
        String memoField = memoJson == null ? "" : ",\"memo\":" + memoJson;
        String actionField = photoAction == null ? "" : ",\"photoAction\":\"" + photoAction + "\"";
        return "{\"mealDate\":\"%s\",\"mealType\":\"%s\"%s%s%s}"
            .formatted(mealDate, mealType, eatenAtField, memoField, actionField);
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
