package com.mealtalk.api.domain.meal.service;

import com.mealtalk.api.domain.food.entity.Food;
import com.mealtalk.api.domain.food.repository.FoodRepository;
import com.mealtalk.api.domain.meal.dto.MealItemRequest;
import com.mealtalk.api.domain.meal.dto.MealListResponse;
import com.mealtalk.api.domain.meal.dto.MealRequest;
import com.mealtalk.api.domain.meal.dto.MealResponse;
import com.mealtalk.api.domain.meal.entity.Meal;
import com.mealtalk.api.domain.meal.entity.MealItem;
import com.mealtalk.api.domain.meal.repository.MealItemRepository;
import com.mealtalk.api.domain.meal.repository.MealRepository;
import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealService {
    private static final int SNAPSHOT_SCALE = 3;

    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final FoodRepository foodRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MealListResponse list(Long userId, LocalDate date) {
        List<MealResponse> meals = mealRepository.findAllByUserIdAndMealDateOrdered(userId, date).stream()
            .map(this::responseFor)
            .toList();
        return MealListResponse.from(date, meals);
    }

    @Transactional(readOnly = true)
    public MealResponse get(Long userId, Long mealId) {
        return responseFor(findOwnedMeal(userId, mealId));
    }

    @Transactional
    public MealResponse create(Long userId, MealRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        List<Food> foods = resolveActiveOwnedFoods(userId, request.items());
        Meal meal = mealRepository.save(Meal.create(user, request.mealDate(), request.mealType(), request.eatenAt(), null));
        mealItemRepository.saveAll(createItems(meal, request.items(), foods));
        return responseFor(meal);
    }

    @Transactional
    public MealResponse update(Long userId, Long mealId, MealRequest request) {
        Meal meal = findOwnedMeal(userId, mealId);
        List<Food> foods = resolveActiveOwnedFoods(userId, request.items());
        meal.update(request.mealDate(), request.mealType(), request.eatenAt(), null);
        mealItemRepository.deleteAllByMealId(mealId);
        mealItemRepository.saveAll(createItems(meal, request.items(), foods));
        return responseFor(meal);
    }

    @Transactional
    public void delete(Long userId, Long mealId) {
        Meal meal = findOwnedMeal(userId, mealId);
        mealItemRepository.deleteAllByMealId(mealId);
        mealRepository.delete(meal);
    }

    private MealResponse responseFor(Meal meal) {
        return MealResponse.from(meal, mealItemRepository.findAllByMealIdOrderByIdAsc(meal.getId()));
    }

    private Meal findOwnedMeal(Long userId, Long mealId) {
        return mealRepository.findByIdAndUserId(mealId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
    }

    private List<Food> resolveActiveOwnedFoods(Long userId, List<MealItemRequest> requests) {
        return requests.stream()
            .map(request -> foodRepository.findByIdAndUserIdAndArchivedFalse(request.foodId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found")))
            .toList();
    }

    private List<MealItem> createItems(Meal meal, List<MealItemRequest> requests, List<Food> foods) {
        return java.util.stream.IntStream.range(0, requests.size())
            .mapToObj(index -> createItem(meal, foods.get(index), requests.get(index).amount()))
            .toList();
    }

    private MealItem createItem(Meal meal, Food food, BigDecimal amount) {
        BigDecimal ratio = amount.divide(food.getServingAmount(), SNAPSHOT_SCALE + 8, RoundingMode.HALF_UP);
        return MealItem.create(
            meal,
            food,
            amount.setScale(SNAPSHOT_SCALE),
            food.getServingUnit(),
            snapshot(food.getCaloriesKcal(), ratio),
            snapshot(food.getCarbohydratesG(), ratio),
            snapshot(food.getProteinG(), ratio),
            snapshot(food.getFatG(), ratio)
        );
    }

    private BigDecimal snapshot(BigDecimal nutrient, BigDecimal ratio) {
        return nutrient.multiply(ratio).setScale(SNAPSHOT_SCALE, RoundingMode.HALF_UP);
    }
}
