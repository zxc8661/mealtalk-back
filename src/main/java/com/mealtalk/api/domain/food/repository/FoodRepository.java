package com.mealtalk.api.domain.food.repository;

import com.mealtalk.api.domain.food.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {
    Optional<Food> findByIdAndUserIdAndArchivedFalse(Long id, Long userId);

    List<Food> findAllByUserIdAndArchivedFalseAndNormalizedNameContainingOrderByNameAsc(
        Long userId,
        String normalizedName
    );

    Optional<Food> findByUserIdAndExternalSourceAndExternalFoodId(
        Long userId,
        String externalSource,
        String externalFoodId
    );
}
