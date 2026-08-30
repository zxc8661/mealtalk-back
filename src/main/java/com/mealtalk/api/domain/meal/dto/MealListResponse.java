package com.mealtalk.api.domain.meal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public record MealListResponse(
    LocalDate mealDate,
    List<MealResponse> meals,
    BigDecimal totalCaloriesKcal,
    BigDecimal totalCarbohydratesG,
    BigDecimal totalProteinG,
    BigDecimal totalFatG
) {
    public static MealListResponse from(LocalDate mealDate, List<MealResponse> meals) {
        return new MealListResponse(
            mealDate,
            meals,
            total(meals, MealResponse::totalCaloriesKcal),
            total(meals, MealResponse::totalCarbohydratesG),
            total(meals, MealResponse::totalProteinG),
            total(meals, MealResponse::totalFatG)
        );
    }

    private static BigDecimal total(List<MealResponse> meals, Function<MealResponse, BigDecimal> value) {
        return meals.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3);
    }
}
