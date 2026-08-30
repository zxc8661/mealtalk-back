package com.mealtalk.api.domain.food.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FoodRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull @Positive @Digits(integer = 7, fraction = 3) BigDecimal servingAmount,
    @NotBlank @Size(max = 20) String servingUnit,
    @NotNull @DecimalMin(value = "0.000") @Digits(integer = 7, fraction = 3) BigDecimal caloriesKcal,
    @NotNull @DecimalMin(value = "0.000") @Digits(integer = 7, fraction = 3) BigDecimal carbohydratesG,
    @NotNull @DecimalMin(value = "0.000") @Digits(integer = 7, fraction = 3) BigDecimal proteinG,
    @NotNull @DecimalMin(value = "0.000") @Digits(integer = 7, fraction = 3) BigDecimal fatG
) {
    @AssertTrue(message = "name and servingUnit must be trimmed")
    public boolean hasTrimmedTextFields() {
        return (name == null || name.equals(name.trim()))
            && (servingUnit == null || servingUnit.equals(servingUnit.trim()));
    }
}
