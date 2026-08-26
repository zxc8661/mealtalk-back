package com.mealtalk.api.domain.auth.dto;

import com.mealtalk.api.domain.user.entity.ActivityLevel;
import com.mealtalk.api.domain.user.entity.GoalMode;
import com.mealtalk.api.domain.user.entity.TargetType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CurrentUserResponse(
    Long id,
    String email,
    String name,
    boolean profileCompleted,
    String timezone,
    ProfileSummary profile,
    List<TargetSummary> targets
) {
    public record ProfileSummary(
        BigDecimal heightCm,
        BigDecimal weightKg,
        ActivityLevel activityLevel,
        GoalMode goalMode
    ) {
    }

    public record TargetSummary(
        TargetType targetType,
        BigDecimal targetValue,
        LocalDate dueDate
    ) {
    }
}
