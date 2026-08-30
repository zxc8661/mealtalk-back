package com.mealtalk.api.domain.food.service;

import com.mealtalk.api.domain.food.dto.FoodRequest;
import com.mealtalk.api.domain.food.dto.FoodResponse;
import com.mealtalk.api.domain.food.entity.Food;
import com.mealtalk.api.domain.food.repository.FoodRepository;
import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FoodService {
    private final FoodRepository foodRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<FoodResponse> list(Long userId, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return foodRepository.findAllByUserIdAndArchivedFalseAndNormalizedNameContainingOrderByNameAsc(
                userId,
                normalizedQuery
            ).stream()
            .map(FoodResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public FoodResponse get(Long userId, Long foodId) {
        return FoodResponse.from(findActiveOwnedFood(userId, foodId));
    }

    @Transactional
    public FoodResponse create(Long userId, FoodRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        Food food = Food.create(
            user,
            request.name(),
            request.name().toLowerCase(Locale.ROOT),
            request.servingAmount(),
            request.servingUnit(),
            request.caloriesKcal(),
            request.carbohydratesG(),
            request.proteinG(),
            request.fatG(),
            null,
            null,
            null
        );
        return FoodResponse.from(foodRepository.save(food));
    }

    @Transactional
    public FoodResponse update(Long userId, Long foodId, FoodRequest request) {
        Food food = findActiveOwnedFood(userId, foodId);
        food.update(
            request.name(),
            request.servingAmount(),
            request.servingUnit(),
            request.caloriesKcal(),
            request.carbohydratesG(),
            request.proteinG(),
            request.fatG()
        );
        return FoodResponse.from(food);
    }

    @Transactional
    public void archive(Long userId, Long foodId) {
        findActiveOwnedFood(userId, foodId).archive();
    }

    private Food findActiveOwnedFood(Long userId, Long foodId) {
        return foodRepository.findByIdAndUserIdAndArchivedFalse(foodId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));
    }
}
