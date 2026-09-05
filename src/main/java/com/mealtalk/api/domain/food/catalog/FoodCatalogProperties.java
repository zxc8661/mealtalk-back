package com.mealtalk.api.domain.food.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the public food nutrition catalog.
 *
 * <p>The defaults target the 식품의약품안전처 식품영양성분DB (data.go.kr operation
 * {@code FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02}), which is the operation the
 * portal's general service key is issued for.
 */
@ConfigurationProperties(prefix = "mealtalk.food-catalog")
public record FoodCatalogProperties(
    String baseUrl,
    String apiKey,
    int maxResults
) {
    public FoodCatalogProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02";
        }
        if (maxResults < 1 || maxResults > 100) {
            maxResults = 20;
        }
    }
}
