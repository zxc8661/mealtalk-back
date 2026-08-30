package com.mealtalk.api.domain.food.entity;

import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Getter
@Entity
@Table(
    name = "foods",
    indexes = @Index(
        name = "idx_foods_user_archived_normalized_name",
        columnList = "user_id, archived, normalized_name"
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Food extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false) private boolean archived;
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

    public static Food create(
        User user,
        String name,
        String normalizedName,
        BigDecimal servingAmount,
        String servingUnit,
        BigDecimal caloriesKcal,
        BigDecimal carbohydratesG,
        BigDecimal proteinG,
        BigDecimal fatG,
        String externalSource,
        String externalFoodId,
        LocalDate lastFetchedAt
    ) {
        Food food = new Food();
        food.user = user;
        food.archived = false;
        food.name = name;
        food.normalizedName = normalizedName;
        food.servingAmount = servingAmount;
        food.servingUnit = servingUnit;
        food.caloriesKcal = caloriesKcal;
        food.carbohydratesG = carbohydratesG;
        food.proteinG = proteinG;
        food.fatG = fatG;
        food.externalSource = externalSource;
        food.externalFoodId = externalFoodId;
        food.lastFetchedAt = lastFetchedAt;
        return food;
    }

    public void update(
        String name,
        BigDecimal servingAmount,
        String servingUnit,
        BigDecimal caloriesKcal,
        BigDecimal carbohydratesG,
        BigDecimal proteinG,
        BigDecimal fatG
    ) {
        this.name = name;
        this.normalizedName = name.toLowerCase(Locale.ROOT);
        this.servingAmount = servingAmount;
        this.servingUnit = servingUnit;
        this.caloriesKcal = caloriesKcal;
        this.carbohydratesG = carbohydratesG;
        this.proteinG = proteinG;
        this.fatG = fatG;
    }

    public void archive() {
        archived = true;
    }
}
