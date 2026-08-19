package com.mealtalk.api.domain.meal.entity;

import com.mealtalk.api.domain.food.entity.Food;
import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Getter @Entity @Table(name = "meal_items") @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "meal_id", nullable = false) private Meal meal;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "food_id", nullable = false) private Food food;
    @Column(nullable = false, precision = 10, scale = 3) private BigDecimal amount;
    @Column(nullable = false, length = 20) private String unit;
    @Column(name = "calories_kcal", nullable = false, precision = 10, scale = 3) private BigDecimal caloriesKcal;
    @Column(name = "carbohydrates_g", nullable = false, precision = 10, scale = 3) private BigDecimal carbohydratesG;
    @Column(name = "protein_g", nullable = false, precision = 10, scale = 3) private BigDecimal proteinG;
    @Column(name = "fat_g", nullable = false, precision = 10, scale = 3) private BigDecimal fatG;
}
