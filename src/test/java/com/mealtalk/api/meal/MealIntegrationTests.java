package com.mealtalk.api.meal;

import com.mealtalk.api.domain.auth.google.GoogleTokenPayload;
import com.mealtalk.api.domain.auth.google.GoogleTokenVerifier;
import com.mealtalk.api.domain.food.repository.FoodRepository;
import com.mealtalk.api.domain.meal.repository.MealItemRepository;
import com.mealtalk.api.domain.meal.repository.MealRepository;
import com.mealtalk.api.domain.user.repository.UserProfileRepository;
import com.mealtalk.api.domain.user.repository.UserRepository;
import com.mealtalk.api.domain.user.repository.UserTargetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void ownerCanCreateReadAndListMealsWithServerDerivedSnapshotsAndDailyTotals() throws Exception {
        String token = login("meal-owner-token", "meal-owner", "owner@example.com");
        long chickenId = createFood(token, "Chicken breast", "100", "g", "165.2", "0", "31.2", "3.6");
        long oatsId = createFood(token, "Oats", "100", "g", "389", "66.3", "16.9", "6.9");

        long lunchId = createMealId(token, mealPayload("2026-08-29", "LUNCH", "2026-08-29T12:30:00Z", List.of(
            item(chickenId, "150", "Attacker name", "kg", "999"),
            item(oatsId, "50", "Attacker name", "kg", "999")
        )));
        long breakfastId = createMealId(token, mealPayload("2026-08-29", "BREAKFAST", "2026-08-29T08:00:00Z", List.of(
            item(chickenId, "100", null, null, null)
        )));
        long untimedId = createMealId(token, mealPayload("2026-08-29", "DINNER", null, List.of(
            item(oatsId, "100", null, null, null)
        )));

        mockMvc.perform(get("/api/v1/meals/{mealId}", lunchId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(lunchId))
            .andExpect(jsonPath("$.mealDate").value("2026-08-29"))
            .andExpect(jsonPath("$.mealType").value("LUNCH"))
            .andExpect(jsonPath("$.eatenAt").value("2026-08-29T12:30:00Z"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].foodId").value(chickenId))
            .andExpect(jsonPath("$.items[0].foodName").value("Chicken breast"))
            .andExpect(jsonPath("$.items[0].unit").value("g"))
            .andExpect(jsonPath("$.items[0].caloriesKcal").value(247.8))
            .andExpect(jsonPath("$.items[0].proteinG").value(46.8))
            .andExpect(jsonPath("$.items[0].name").doesNotExist())
            .andExpect(jsonPath("$.totalCaloriesKcal").value(442.3))
            .andExpect(jsonPath("$.totalCarbohydratesG").value(33.15))
            .andExpect(jsonPath("$.totalProteinG").value(55.25))
            .andExpect(jsonPath("$.totalFatG").value(8.85));

        mockMvc.perform(get("/api/v1/meals?date=2026-08-29")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mealDate").value("2026-08-29"))
            .andExpect(jsonPath("$.meals.length()").value(3))
            .andExpect(jsonPath("$.meals[0].id").value(breakfastId))
            .andExpect(jsonPath("$.meals[1].id").value(lunchId))
            .andExpect(jsonPath("$.meals[2].id").value(untimedId))
            .andExpect(jsonPath("$.totalCaloriesKcal").value(996.5))
            .andExpect(jsonPath("$.totalCarbohydratesG").value(99.45))
            .andExpect(jsonPath("$.totalProteinG").value(103.35))
            .andExpect(jsonPath("$.totalFatG").value(19.35));
    }

    @Test
    void ownerCanAtomicallyReplaceAllItemsAndDeleteMealChildren() throws Exception {
        String token = login("meal-update-token", "meal-update", "update@example.com");
        long chickenId = createFood(token, "Chicken", "100", "g", "165.2", "0", "31.2", "3.6");
        long oatsId = createFood(token, "Oats", "100", "g", "389", "66.3", "16.9", "6.9");
        long mealId = createMealId(token, mealPayload("2026-08-29", "LUNCH", "2026-08-29T12:00:00Z", List.of(
            item(chickenId, "100", null, null, null)
        )));
        List<Long> originalItemIds = mealItemRepository.findAllByMealId(mealId).stream().map(item -> item.getId()).toList();

        mockMvc.perform(put("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mealPayload("2026-08-30", "DINNER", null, List.of(item(oatsId, "75", null, null, null)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(mealId))
            .andExpect(jsonPath("$.mealDate").value("2026-08-30"))
            .andExpect(jsonPath("$.mealType").value("DINNER"))
            .andExpect(jsonPath("$.eatenAt").doesNotExist())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].foodId").value(oatsId))
            .andExpect(jsonPath("$.items[0].amount").value(75.0))
            .andExpect(jsonPath("$.items[0].caloriesKcal").value(291.75));

        assertThat(mealItemRepository.findAllByMealId(mealId))
            .hasSize(1)
            .extracting(item -> item.getFood().getId())
            .containsExactly(oatsId);
        assertThat(mealItemRepository.findAllByMealId(mealId).stream().map(item -> item.getId()))
            .doesNotContainAnyElementsOf(originalItemIds);

        mockMvc.perform(delete("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        assertThat(mealItemRepository.findAllByMealId(mealId)).isEmpty();
        mockMvc.perform(get("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void enforcesAuthenticationOwnershipActiveFoodsAndAggregateValidation() throws Exception {
        mockMvc.perform(get("/api/v1/meals?date=2026-08-29"))
            .andExpect(status().isUnauthorized());

        String ownerToken = login("meal-validation-owner", "meal-validation-owner", "owner@example.com");
        String otherToken = login("meal-validation-other", "meal-validation-other", "other@example.com");
        long ownerFoodId = createFood(ownerToken, "Owner food", "100", "g", "100", "10", "10", "10");
        long otherFoodId = createFood(otherToken, "Other food", "100", "g", "100", "10", "10", "10");
        long mealId = createMealId(ownerToken, mealPayload("2026-08-29", "LUNCH", null, List.of(item(ownerFoodId, "100", null, null, null))));

        mockMvc.perform(post("/api/v1/meals")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mealPayload("2026-08-29", "LUNCH", null, List.of(
                    item(ownerFoodId, "100", null, null, null), item(ownerFoodId, "50", null, null, null)
                ))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/meals")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"items\":[]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/meals")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mealPayload("2026-08-29", "LUNCH", null, List.of(item(otherFoodId, "100", null, null, null)))) )
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mealPayload("2026-08-30", "DINNER", null, List.of(item(ownerFoodId, "50", null, null, null)))) )
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/meals/{mealId}", mealId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/foods/{foodId}", ownerFoodId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/meals")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mealPayload("2026-08-29", "LUNCH", null, List.of(item(ownerFoodId, "100", null, null, null)))) )
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/meals?date=not-a-date")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isBadRequest());
    }

    private long createFood(
        String token,
        String name,
        String servingAmount,
        String servingUnit,
        String caloriesKcal,
        String carbohydratesG,
        String proteinG,
        String fatG
    ) throws Exception {
        String response = mockMvc.perform(post("/api/v1/foods")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","servingAmount":%s,"servingUnit":"%s","caloriesKcal":%s,"carbohydratesG":%s,"proteinG":%s,"fatG":%s}
                    """.formatted(name, servingAmount, servingUnit, caloriesKcal, carbohydratesG, proteinG, fatG)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return extractId(response);
    }

    private long createMealId(String token, String payload) throws Exception {
        String response = mockMvc.perform(post("/api/v1/meals")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return extractId(response);
    }

    private String mealPayload(String mealDate, String mealType, String eatenAt, List<String> items) {
        String eatenAtField = eatenAt == null ? "" : ",\"eatenAt\":\"" + eatenAt + "\"";
        return "{\"mealDate\":\"%s\",\"mealType\":\"%s\"%s,\"items\":[%s]}"
            .formatted(mealDate, mealType, eatenAtField, String.join(",", items));
    }

    private String item(Long foodId, String amount, String name, String unit, String caloriesKcal) {
        String ignoredFields = name == null ? "" : ",\"name\":\"%s\",\"unit\":\"%s\",\"caloriesKcal\":%s".formatted(name, unit, caloriesKcal);
        return "{\"foodId\":%d,\"amount\":%s%s}".formatted(foodId, amount, ignoredFields);
    }

    private long extractId(String response) throws Exception {
        return new ObjectMapper().readTree(response).required("id").longValue();
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
