package com.mealtalk.api.domain.meal.repository;

import com.mealtalk.api.domain.meal.entity.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealItemRepository extends JpaRepository<MealItem, Long> {
    List<MealItem> findAllByMealId(Long mealId);
}
