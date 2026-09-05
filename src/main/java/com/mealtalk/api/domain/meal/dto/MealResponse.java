package com.mealtalk.api.domain.meal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mealtalk.api.domain.meal.entity.Meal;
import com.mealtalk.api.domain.meal.entity.MealPhoto;
import com.mealtalk.api.domain.meal.entity.MealType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One meal record. It reports only what the user supplied - date, type, an
 * optional eaten time, the memo, and the metadata of the single photo. Nothing
 * here is derived from a nutrition database, so no calorie or macro figure is
 * ever returned.
 *
 * <p>{@code photo} is absent (not null-valued) on a memo-only record, and never
 * carries a storage key or URL - only the authenticated path the owner can call.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MealResponse(
    Long id,
    LocalDate mealDate,
    MealType mealType,
    Instant eatenAt,
    String memo,
    MealPhotoResponse photo
) {
    public static MealResponse from(Meal meal) {
        return from(meal, null);
    }

    public static MealResponse from(Meal meal, MealPhoto photo) {
        return new MealResponse(
            meal.getId(),
            meal.getMealDate(),
            meal.getMealType(),
            meal.getEatenAt(),
            meal.getSourceText(),
            photo == null ? null : MealPhotoResponse.from(photo, meal.getId())
        );
    }
}
