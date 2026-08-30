package com.mealtalk.api.domain.meal.repository;

import com.mealtalk.api.domain.meal.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealRepository extends JpaRepository<Meal, Long> {
    @Query("""
        select m from Meal m
        where m.user.id = :userId and m.mealDate = :mealDate
        order by case when m.eatenAt is null then 1 else 0 end, m.eatenAt asc, m.id asc
        """)
    List<Meal> findAllByUserIdAndMealDateOrdered(Long userId, LocalDate mealDate);

    Optional<Meal> findByIdAndUserId(Long id, Long userId);

    List<Meal> findAllByUserIdAndMealDateOrderByEatenAtAscCreatedAtAsc(Long userId, LocalDate mealDate);
}
