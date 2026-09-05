package com.mealtalk.api.domain.meal.repository;

import com.mealtalk.api.domain.meal.entity.MealPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MealPhotoRepository extends JpaRepository<MealPhoto, Long> {
    Optional<MealPhoto> findByMealId(Long mealId);

    List<MealPhoto> findAllByMealIdIn(List<Long> mealIds);

    void deleteByMealId(Long mealId);
}
