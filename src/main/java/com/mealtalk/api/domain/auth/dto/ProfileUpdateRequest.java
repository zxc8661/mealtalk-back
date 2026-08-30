package com.mealtalk.api.domain.auth.dto;

import com.mealtalk.api.domain.user.entity.ActivityLevel;
import com.mealtalk.api.domain.user.entity.GoalMode;
import com.mealtalk.api.domain.user.entity.TargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfileUpdateRequest(
    @NotNull @Positive BigDecimal heightCm,
    @NotNull @Positive BigDecimal weightKg,
    @NotNull ActivityLevel activityLevel,
    @NotNull GoalMode goalMode,
    @NotNull List<@Valid TargetRequest> targets
) {
    public record TargetRequest(
        @NotNull TargetType targetType,
        @NotNull @Positive BigDecimal targetValue,
        @Future LocalDate dueDate
    ) {
    }
}
