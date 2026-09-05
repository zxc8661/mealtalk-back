package com.mealtalk.api.domain.food.catalog;

import java.math.BigDecimal;

public record FoodCatalogResponse(
    String sourceId,
    String name,
    String foodGroup,
    BigDecimal servingAmount,
    BigDecimal caloriesKcal,
    BigDecimal carbohydratesG,
    BigDecimal proteinG,
    BigDecimal fatG
) {
}
