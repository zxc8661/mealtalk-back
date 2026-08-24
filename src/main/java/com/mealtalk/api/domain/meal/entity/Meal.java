package com.mealtalk.api.domain.meal.entity;

import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;

@Getter @Entity @Table(name = "meals", indexes = @Index(name = "idx_meals_user_date_eaten", columnList = "user_id, meal_date, eaten_at")) @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meal extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "meal_date", nullable = false) private LocalDate mealDate;
    @Enumerated(EnumType.STRING) @Column(name = "meal_type", nullable = false, length = 20) private MealType mealType;
    @Column(name = "eaten_at") private Instant eatenAt;
    @Column(name = "source_text", columnDefinition = "TEXT") private String sourceText;

    public static Meal create(User user, LocalDate mealDate, MealType mealType, Instant eatenAt, String sourceText) {
        Meal meal = new Meal();
        meal.user = user;
        meal.mealDate = mealDate;
        meal.mealType = mealType;
        meal.eatenAt = eatenAt;
        meal.sourceText = sourceText;
        return meal;
    }
}
