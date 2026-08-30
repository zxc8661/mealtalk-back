package com.mealtalk.api.food;

import com.mealtalk.api.domain.auth.google.GoogleTokenPayload;
import com.mealtalk.api.domain.auth.google.GoogleTokenVerifier;
import com.mealtalk.api.domain.food.repository.FoodRepository;
import com.mealtalk.api.domain.user.repository.UserProfileRepository;
import com.mealtalk.api.domain.user.repository.UserRepository;
import com.mealtalk.api.domain.user.repository.UserTargetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
class FoodIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private FoodRepository foodRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserTargetRepository userTargetRepository;
    @MockitoBean private GoogleTokenVerifier googleTokenVerifier;

    @AfterEach
    void clearData() {
        foodRepository.deleteAll();
        userTargetRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanCreateSearchReadAndFullyUpdateFood() throws Exception {
        String token = login("food-owner-token", "food-owner", "owner@example.com");
        long foodId = createId(token, validFood("Chicken breast", "100.000", "g"));

        mockMvc.perform(get("/api/v1/foods?query=chicken")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(foodId))
            .andExpect(jsonPath("$[0].name").value("Chicken breast"));
        mockMvc.perform(get("/api/v1/foods/{foodId}", foodId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.servingUnit").value("g"))
            .andExpect(jsonPath("$.proteinG").value(31.2));
        mockMvc.perform(put("/api/v1/foods/{foodId}", foodId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFood("Grilled chicken", "120", "grams")
                    .replace("165.2", "250.125")
                    .replace("31.2", "42.875")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Grilled chicken"))
            .andExpect(jsonPath("$.servingAmount").value(120.0))
            .andExpect(jsonPath("$.servingUnit").value("grams"))
            .andExpect(jsonPath("$.caloriesKcal").value(250.125))
            .andExpect(jsonPath("$.proteinG").value(42.875));
        mockMvc.perform(get("/api/v1/foods?query=breast")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void foreignFoodIsInvisibleAndCannotBeMutated() throws Exception {
        String ownerToken = login("owner-food-token", "food-owner", "owner@example.com");
        String otherToken = login("other-food-token", "food-other", "other@example.com");
        long foodId = createId(ownerToken, validFood("Owner food", "100", "g"));

        mockMvc.perform(get("/api/v1/foods/{foodId}", foodId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/foods/{foodId}", foodId)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFood("Hijacked", "1", "g")))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/foods/{foodId}", foodId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/foods")
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/foods/{foodId}", foodId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Owner food"));
    }

    @Test
    void archiveExcludesFoodFromListAndDetail() throws Exception {
        String token = login("archive-food-token", "food-owner", "owner@example.com");
        long foodId = createId(token, validFood("Archive me", "100", "g"));

        mockMvc.perform(delete("/api/v1/foods/{foodId}", foodId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/foods?query=archive")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/foods/{foodId}", foodId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());

        assertThat(foodRepository.findById(foodId)).isPresent();
        assertThat(foodRepository.findById(foodId).orElseThrow().isArchived()).isTrue();
    }

    @Test
    void rejectsAnonymousMalformedAndOverPrecisionPayloads() throws Exception {
        mockMvc.perform(get("/api/v1/foods"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/foods")
                .header("Authorization", "Bearer malformed-token"))
            .andExpect(status().isUnauthorized());

        String token = login("invalid-food-token", "food-owner", "owner@example.com");
        mockMvc.perform(post("/api/v1/foods")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/foods/{foodId}", 999999L)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/foods")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFood(" Chicken ", "100", "g")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/foods")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFood("Chicken", "100.0001", "g")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/foods")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validFood("Chicken", "0", "g").replace("\"proteinG\":31.2", "\"proteinG\":-0.001")))
            .andExpect(status().isBadRequest());

        assertThat(foodRepository.findAll()).isEmpty();
    }

    @Test
    void ignoresClientControlledOwnershipMetadataAndTotals() throws Exception {
        String token = login("untrusted-food-token", "food-owner", "owner@example.com");
        String payload = validFood("Trusted food", "100", "g").replace("\"fatG\": 3.6", """
            "fatG": 3.6,
            "userId": 999999,
            "normalizedName": "attacker value",
            "externalSource": "attacker source",
            "externalFoodId": "attacker id",
            "lastFetchedAt": "2020-01-01",
            "totalCaloriesKcal": 9999,
            "totalCarbohydratesG": 9999,
            "totalProteinG": 9999,
            "totalFatG": 9999
            """);

        String response = create(token, payload)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Trusted food"))
            .andExpect(jsonPath("$.caloriesKcal").value(165.2))
            .andExpect(jsonPath("$.userId").doesNotExist())
            .andExpect(jsonPath("$.normalizedName").doesNotExist())
            .andExpect(jsonPath("$.externalSource").doesNotExist())
            .andExpect(jsonPath("$.externalFoodId").doesNotExist())
            .andExpect(jsonPath("$.lastFetchedAt").doesNotExist())
            .andExpect(jsonPath("$.totalCaloriesKcal").doesNotExist())
            .andExpect(jsonPath("$.totalCarbohydratesG").doesNotExist())
            .andExpect(jsonPath("$.totalProteinG").doesNotExist())
            .andExpect(jsonPath("$.totalFatG").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long foodId = extractId(response);
        var persisted = foodRepository.findById(foodId).orElseThrow();

        Long ownerId = userRepository.findAll().stream()
            .filter(user -> user.getEmail().equals("owner@example.com"))
            .findFirst()
            .orElseThrow()
            .getId();
        assertThat(persisted.getUser().getId()).isEqualTo(ownerId);
        assertThat(persisted.getNormalizedName()).isEqualTo("trusted food");
        assertThat(persisted.getExternalSource()).isNull();
        assertThat(persisted.getExternalFoodId()).isNull();
        assertThat(persisted.getLastFetchedAt()).isNull();
    }

    private long createId(String token, String content) throws Exception {
        String response = create(token, content)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return extractId(response);
    }

    private long extractId(String response) {
        return Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private org.springframework.test.web.servlet.ResultActions create(String token, String content) throws Exception {
        return mockMvc.perform(post("/api/v1/foods")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(content));
    }

    private String login(String token, String subject, String email) throws Exception {
        when(googleTokenVerifier.verify(token)).thenReturn(new GoogleTokenPayload(subject, email, "Food User"));
        String response = mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + token + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private String validFood(String name, String servingAmount, String unit) {
        return """
            {
              "name": "%s",
              "servingAmount": %s,
              "servingUnit": "%s",
              "caloriesKcal": 165.2,
              "carbohydratesG": 0,
              "proteinG": 31.2,
              "fatG": 3.6
            }
            """.formatted(name, servingAmount, unit);
    }
}
