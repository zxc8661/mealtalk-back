package com.mealtalk.api.domain.meal.repository;

import com.mealtalk.api.domain.meal.entity.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MealItemRepository extends JpaRepository<MealItem, Long> {
    List<MealItem> findAllByMealId(Long mealId);

    List<MealItem> findAllByMealIdOrderByIdAsc(Long mealId);

    @Modifying
    @Query("delete from MealItem item where item.meal.id = :mealId")
    void deleteAllByMealId(Long mealId);
}
