package com.mealtalk.api.domain.meal.dto;

import com.mealtalk.api.domain.meal.entity.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

public record MealRequest(
    @NotNull LocalDate mealDate,
    @NotNull MealType mealType,
    Instant eatenAt,
    @NotEmpty @Size(max = 50) List<@NotNull @Valid MealItemRequest> items
) {
    @AssertTrue(message = "foodIds must be unique")
    public boolean hasUniqueFoodIds() {
        return items == null || items.stream().map(MealItemRequest::foodId).distinct().count() == items.size();
    }
}
