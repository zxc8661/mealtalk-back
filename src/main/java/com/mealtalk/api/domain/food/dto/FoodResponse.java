package com.mealtalk.api.domain.food.dto;

import com.mealtalk.api.domain.food.entity.Food;

import java.math.BigDecimal;

public record FoodResponse(
    Long id,
    String name,
    BigDecimal servingAmount,
    String servingUnit,
    BigDecimal caloriesKcal,
    BigDecimal carbohydratesG,
    BigDecimal proteinG,
    BigDecimal fatG
) {
    public static FoodResponse from(Food food) {
        return new FoodResponse(
            food.getId(),
            food.getName(),
            food.getServingAmount(),
            food.getServingUnit(),
            food.getCaloriesKcal(),
            food.getCarbohydratesG(),
            food.getProteinG(),
            food.getFatG()
        );
    }
}
