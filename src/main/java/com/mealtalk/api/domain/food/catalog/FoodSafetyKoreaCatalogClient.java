package com.mealtalk.api.domain.food.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the public 식품영양성분DB through the data.go.kr nutrition operation.
 *
 * <p>The portal issues service keys already percent-encoded for pasting into a URL,
 * so the key is decoded once here and re-encoded by the URI builder. Encoding it
 * twice makes the provider reject the request.
 */
@Component
public class FoodSafetyKoreaCatalogClient {
    private static final String SERVING_SIZE_FIELD = "SERVING_SIZE";
    private static final String CALORIES_FIELD = "AMT_NUM1";
    private static final String CARBOHYDRATE_FIELD = "AMT_NUM6";
    private static final String PROTEIN_FIELD = "AMT_NUM3";
    private static final String FAT_FIELD = "AMT_NUM4";

    private final FoodCatalogProperties properties;
    private final RestClient restClient;

    public FoodSafetyKoreaCatalogClient(FoodCatalogProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    public List<FoodCatalogResponse> search(String query) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw unavailable("Food catalog API key is not configured", null);
        }

        JsonNode body;
        try {
            body = restClient.get()
                .uri(requestUri(query))
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw unavailable("Food catalog provider request failed", exception);
        }

        if (body == null) {
            throw unavailable("Food catalog provider returned an empty response", null);
        }
        JsonNode header = body.path("header");
        if (!header.isMissingNode() && !"00".equals(header.path("resultCode").asText())) {
            throw unavailable("Food catalog provider rejected the request", null);
        }
        JsonNode responseBody = body.path("body");
        if (responseBody.isMissingNode()) {
            throw unavailable("Food catalog provider returned an invalid response", null);
        }
        // A search that matches nothing omits "items" entirely; that is an empty
        // result, not a provider failure.
        JsonNode items = responseBody.path("items");
        if (!items.isArray()) {
            return List.of();
        }

        // The provider lists one row per source and survey year, so the same food
        // repeats under different codes. Rows a reader cannot tell apart collapse;
        // rows sharing only a name keep their own entry because their nutrition differs.
        Set<String> seen = new HashSet<>();
        List<FoodCatalogResponse> foods = new ArrayList<>();
        for (JsonNode item : items) {
            foodFrom(item)
                .filter(food -> seen.add(displaySignature(food)))
                .ifPresent(foods::add);
        }
        return foods;
    }

    private String displaySignature(FoodCatalogResponse food) {
        return String.join("|",
            food.name(),
            food.servingAmount().stripTrailingZeros().toPlainString(),
            food.caloriesKcal().stripTrailingZeros().toPlainString(),
            food.carbohydratesG().stripTrailingZeros().toPlainString(),
            food.proteinG().stripTrailingZeros().toPlainString(),
            food.fatG().stripTrailingZeros().toPlainString()
        );
    }

    /**
     * The key is built pre-encoded rather than through {@code queryParam}: the URI
     * builder leaves {@code +} untouched, and a provider reading the query as form
     * data would decode that plus sign back into a space and reject the key.
     */
    private URI requestUri(String query) {
        return UriComponentsBuilder.fromUriString(properties.baseUrl())
            .queryParam("serviceKey", encodedApiKey())
            .queryParam("type", "json")
            .queryParam("pageNo", 1)
            .queryParam("numOfRows", properties.maxResults())
            .queryParam("FOOD_NM_KR", UriUtils.encode(query, StandardCharsets.UTF_8))
            .build(true)
            .toUri();
    }

    /** Portal keys arrive percent-encoded, so decode once before encoding once. */
    private String encodedApiKey() {
        String key = properties.apiKey();
        String decoded = key.contains("%") ? URLDecoder.decode(key, StandardCharsets.UTF_8) : key;
        return UriUtils.encode(decoded, StandardCharsets.UTF_8);
    }

    private Optional<FoodCatalogResponse> foodFrom(JsonNode item) {
        Optional<BigDecimal> servingAmount = decimal(item, SERVING_SIZE_FIELD);
        Optional<BigDecimal> calories = decimal(item, CALORIES_FIELD);
        Optional<BigDecimal> carbohydrates = decimal(item, CARBOHYDRATE_FIELD);
        Optional<BigDecimal> protein = decimal(item, PROTEIN_FIELD);
        Optional<BigDecimal> fat = decimal(item, FAT_FIELD);
        String sourceId = item.path("FOOD_CD").asText().trim();
        String name = item.path("FOOD_NM_KR").asText().trim();

        if (sourceId.isEmpty() || name.isEmpty()
            || servingAmount.isEmpty() || calories.isEmpty() || carbohydrates.isEmpty()
            || protein.isEmpty() || fat.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new FoodCatalogResponse(
            sourceId,
            name,
            item.path("FOOD_CAT1_NM").asText().trim(),
            servingAmount.orElseThrow(),
            calories.orElseThrow(),
            carbohydrates.orElseThrow(),
            protein.orElseThrow(),
            fat.orElseThrow()
        ));
    }

    /**
     * Provider amounts arrive as plain decimals, but the serving size carries its
     * unit ("100g"), so the trailing unit is dropped before parsing.
     */
    private Optional<BigDecimal> decimal(JsonNode item, String fieldName) {
        String value = item.path(fieldName).asText().trim().replaceAll("[^0-9.].*$", "");
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            BigDecimal parsed = new BigDecimal(value);
            return parsed.signum() < 0 ? Optional.empty() : Optional.of(parsed);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private ResponseStatusException unavailable(String reason, Exception cause) {
        return cause == null
            ? new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason)
            : new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason, cause);
    }
}
