package com.mealtalk.api.domain.user.entity;

import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter @Entity @Table(name = "user_profiles") @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    @Column(name = "height_cm", nullable = false, precision = 5, scale = 2) private BigDecimal heightCm;
    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2) private BigDecimal weightKg;
    @Enumerated(EnumType.STRING) @Column(name = "activity_level", nullable = false, length = 20) private ActivityLevel activityLevel;
    @Enumerated(EnumType.STRING) @Column(name = "goal_mode", nullable = false, length = 20) private GoalMode goalMode;

    public static UserProfile create(User user, BigDecimal heightCm, BigDecimal weightKg, ActivityLevel activityLevel, GoalMode goalMode) {
        UserProfile profile = new UserProfile();
        profile.user = user;
        profile.heightCm = heightCm;
        profile.weightKg = weightKg;
        profile.activityLevel = activityLevel;
        profile.goalMode = goalMode;
        return profile;
    }

    public void update(BigDecimal heightCm, BigDecimal weightKg, ActivityLevel activityLevel, GoalMode goalMode) {
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.activityLevel = activityLevel;
        this.goalMode = goalMode;
    }
}
