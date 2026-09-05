package com.mealtalk.api.domain.meal.repository;

import com.mealtalk.api.domain.meal.entity.MealWriteRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MealWriteRequestRepository extends JpaRepository<MealWriteRequest, Long> {
    Optional<MealWriteRequest> findByUserIdAndClientRequestId(Long userId, String clientRequestId);

    void deleteAllByMealId(Long mealId);
}
