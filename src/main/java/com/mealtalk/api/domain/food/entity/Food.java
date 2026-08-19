package com.mealtalk.api.domain.food.entity;

import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Entity @Table(name = "foods") @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Food extends BaseEntity {
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "normalized_name", nullable = false, length = 200) private String normalizedName;
    @Column(name = "serving_amount", nullable = false, precision = 10, scale = 3) private BigDecimal servingAmount;
    @Column(name = "serving_unit", nullable = false, length = 20) private String servingUnit;
    @Column(name = "calories_kcal", nullable = false, precision = 10, scale = 3) private BigDecimal caloriesKcal;
    @Column(name = "carbohydrates_g", nullable = false, precision = 10, scale = 3) private BigDecimal carbohydratesG;
    @Column(name = "protein_g", nullable = false, precision = 10, scale = 3) private BigDecimal proteinG;
    @Column(name = "fat_g", nullable = false, precision = 10, scale = 3) private BigDecimal fatG;
    @Column(name = "external_source", length = 50) private String externalSource;
    @Column(name = "external_food_id", length = 200) private String externalFoodId;
    @Column(name = "last_fetched_at") private LocalDate lastFetchedAt;
}
