package com.mealtalk.api.domain.meal.dto;

import com.mealtalk.api.domain.meal.entity.Meal;
import com.mealtalk.api.domain.meal.entity.MealItem;
import com.mealtalk.api.domain.meal.entity.MealType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MealResponse(
    Long id,
    LocalDate mealDate,
    MealType mealType,
    Instant eatenAt,
    List<MealItemResponse> items,
    BigDecimal totalCaloriesKcal,
    BigDecimal totalCarbohydratesG,
    BigDecimal totalProteinG,
    BigDecimal totalFatG
) {
    public static MealResponse from(Meal meal, List<MealItem> items) {
        List<MealItemResponse> itemResponses = items.stream().map(MealItemResponse::from).toList();
        return new MealResponse(
            meal.getId(),
            meal.getMealDate(),
            meal.getMealType(),
            meal.getEatenAt(),
            itemResponses,
            total(itemResponses, MealItemResponse::caloriesKcal),
            total(itemResponses, MealItemResponse::carbohydratesG),
            total(itemResponses, MealItemResponse::proteinG),
            total(itemResponses, MealItemResponse::fatG)
        );
    }

    private static BigDecimal total(List<MealItemResponse> items, java.util.function.Function<MealItemResponse, BigDecimal> value) {
        return items.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3);
    }
}
