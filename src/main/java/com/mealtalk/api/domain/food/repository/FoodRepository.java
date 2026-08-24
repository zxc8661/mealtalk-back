package com.mealtalk.api.domain.food.repository;

import com.mealtalk.api.domain.food.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {
    Optional<Food> findFirstByNormalizedNameOrderByCreatedAtDesc(String normalizedName);

    Optional<Food> findByExternalSourceAndExternalFoodId(String externalSource, String externalFoodId);
}
