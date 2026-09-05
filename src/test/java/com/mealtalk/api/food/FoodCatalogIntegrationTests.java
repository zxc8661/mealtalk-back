package com.mealtalk.api.food;

import com.mealtalk.api.domain.auth.google.GoogleTokenPayload;
import com.mealtalk.api.domain.auth.google.GoogleTokenVerifier;
import com.mealtalk.api.domain.food.catalog.FoodCatalogResponse;
import com.mealtalk.api.domain.food.catalog.FoodCatalogService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FoodCatalogIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserTargetRepository userTargetRepository;
    @MockitoBean private GoogleTokenVerifier googleTokenVerifier;
    @MockitoBean private FoodCatalogService foodCatalogService;

    @AfterEach
    void clearData() {
        userTargetRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void authenticatedUserCanSearchTheExternalFoodCatalog() throws Exception {
        String token = login("catalog-token", "catalog-user", "catalog@example.com");
        when(foodCatalogService.search("닭가슴살")).thenReturn(List.of(new FoodCatalogResponse(
            "1001",
            "닭가슴살, 구운것",
            "육류",
            new BigDecimal("100"),
            new BigDecimal("165"),
            BigDecimal.ZERO,
            new BigDecimal("31"),
            new BigDecimal("3.6")
        )));

        mockMvc.perform(get("/api/v1/foods/catalog")
                .param("query", " 닭가슴살 ")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].sourceId").value("1001"))
            .andExpect(jsonPath("$[0].name").value("닭가슴살, 구운것"))
            .andExpect(jsonPath("$[0].foodGroup").value("육류"))
            .andExpect(jsonPath("$[0].servingAmount").value(100))
            .andExpect(jsonPath("$[0].proteinG").value(31));
    }

    @Test
    void rejectsAnonymousAndBlankCatalogQueries() throws Exception {
        mockMvc.perform(get("/api/v1/foods/catalog").param("query", "닭가슴살"))
            .andExpect(status().isUnauthorized());

        String token = login("blank-catalog-token", "catalog-user", "blank@example.com");
        mockMvc.perform(get("/api/v1/foods/catalog")
                .param("query", " ")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    private String login(String token, String subject, String email) throws Exception {
        when(googleTokenVerifier.verify(token)).thenReturn(new GoogleTokenPayload(subject, email, "Catalog User"));
        String response = mockMvc.perform(post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + token + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
