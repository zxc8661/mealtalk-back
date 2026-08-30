package com.mealtalk.api.domain.meal.dto;

import com.mealtalk.api.domain.meal.entity.MealItem;

import java.math.BigDecimal;

public record MealItemResponse(
    Long id,
    Long foodId,
    String foodName,
    BigDecimal amount,
    String unit,
    BigDecimal caloriesKcal,
    BigDecimal carbohydratesG,
    BigDecimal proteinG,
    BigDecimal fatG
) {
    public static MealItemResponse from(MealItem item) {
        return new MealItemResponse(
            item.getId(),
            item.getFood().getId(),
            item.getFoodName(),
            item.getAmount(),
            item.getUnit(),
            item.getCaloriesKcal(),
            item.getCarbohydratesG(),
            item.getProteinG(),
            item.getFatG()
        );
    }
}
