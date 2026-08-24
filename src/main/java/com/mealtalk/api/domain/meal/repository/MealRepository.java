package com.mealtalk.api.domain.meal.repository;

import com.mealtalk.api.domain.meal.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findAllByUserIdAndMealDateOrderByEatenAtAscCreatedAtAsc(Long userId, LocalDate mealDate);
}
