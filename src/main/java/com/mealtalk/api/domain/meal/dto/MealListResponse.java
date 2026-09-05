package com.mealtalk.api.domain.meal.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The records of one day, in the order the journal shows them. A day has no
 * total to report: the records carry no numbers to add up.
 */
public record MealListResponse(
    LocalDate mealDate,
    List<MealResponse> meals
) {
    public static MealListResponse from(LocalDate mealDate, List<MealResponse> meals) {
        return new MealListResponse(mealDate, meals);
    }
}
