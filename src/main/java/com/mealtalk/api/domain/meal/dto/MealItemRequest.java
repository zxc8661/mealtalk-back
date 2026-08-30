package com.mealtalk.api.domain.meal.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record MealItemRequest(
    @NotNull @Positive Long foodId,
    @NotNull @Positive @Digits(integer = 7, fraction = 3) BigDecimal amount
) {
}
