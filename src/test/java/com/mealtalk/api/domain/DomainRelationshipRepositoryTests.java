package com.mealtalk.api.domain;

import com.mealtalk.api.domain.food.entity.Food;
import com.mealtalk.api.domain.food.repository.FoodRepository;
import com.mealtalk.api.domain.meal.entity.Meal;
import com.mealtalk.api.domain.meal.entity.MealItem;
import com.mealtalk.api.domain.meal.entity.MealType;
import com.mealtalk.api.domain.meal.repository.MealItemRepository;
import com.mealtalk.api.domain.meal.repository.MealRepository;
import com.mealtalk.api.domain.user.entity.ActivityLevel;
import com.mealtalk.api.domain.user.entity.AuthProvider;
import com.mealtalk.api.domain.user.entity.GoalMode;
import com.mealtalk.api.domain.user.entity.TargetType;
import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.domain.user.entity.UserProfile;
import com.mealtalk.api.domain.user.entity.UserTarget;
import com.mealtalk.api.domain.user.repository.UserProfileRepository;
import com.mealtalk.api.domain.user.repository.UserRepository;
import com.mealtalk.api.domain.user.repository.UserTargetRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class DomainRelationshipRepositoryTests {
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserTargetRepository userTargetRepository;
    @Autowired private FoodRepository foodRepository;
    @Autowired private MealRepository mealRepository;
    @Autowired private MealItemRepository mealItemRepository;
    @Autowired private EntityManager entityManager;

    @Test
    public void storesAndQueriesMvpDomainRelationships() {
        User user = userRepository.save(User.create(
            AuthProvider.GOOGLE,
            "google-user-1",
            "minsu@example.com",
            "민수",
            "Asia/Seoul"
        ));
        user.completeProfile();
        User otherUser = userRepository.save(User.create(
            AuthProvider.GOOGLE,
            "google-user-2",
            "other@example.com",
            "Other",
            "UTC"
        ));

        userProfileRepository.save(UserProfile.create(
            user,
            BigDecimal.valueOf(178),
            BigDecimal.valueOf(85),
            ActivityLevel.MEDIUM,
            GoalMode.LOSS
        ));
        userTargetRepository.save(UserTarget.create(
            user,
            TargetType.DAILY_PROTEIN,
            BigDecimal.valueOf(150),
            LocalDate.now().plusMonths(3)
        ));

        Food chicken = foodRepository.save(Food.create(
            user,
            "닭가슴살",
            "닭가슴살",
            BigDecimal.valueOf(100),
            "g",
            BigDecimal.valueOf(165),
            BigDecimal.ZERO,
            BigDecimal.valueOf(31),
            BigDecimal.valueOf(3.6),
            "fixture",
            "chicken-breast",
            LocalDate.now()
        ));
        LocalDate mealDate = LocalDate.of(2026, 8, 17);
        Meal meal = mealRepository.save(Meal.create(
            user,
            mealDate,
            MealType.LUNCH,
            Instant.parse("2026-08-17T03:00:00Z"),
            "점심에 닭가슴살 200g 먹었어"
        ));
        mealItemRepository.save(MealItem.create(
            meal,
            chicken,
            BigDecimal.valueOf(200),
            "g",
            BigDecimal.valueOf(330),
            BigDecimal.ZERO,
            BigDecimal.valueOf(62),
            BigDecimal.valueOf(7.2)
        ));

        assertTrue(userRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-user-1").isPresent());
        assertTrue(userProfileRepository.findByUserId(user.getId()).isPresent());
        assertEquals(1, userTargetRepository.findAllByUserId(user.getId()).size());
        assertTrue(foodRepository.findByUserIdAndExternalSourceAndExternalFoodId(
            user.getId(),
            "fixture",
            "chicken-breast"
        ).isPresent());
        assertTrue(foodRepository.findByIdAndUserIdAndArchivedFalse(chicken.getId(), user.getId()).isPresent());
        assertTrue(foodRepository.findByIdAndUserIdAndArchivedFalse(chicken.getId(), otherUser.getId()).isEmpty());
        assertEquals(1, foodRepository
            .findAllByUserIdAndArchivedFalseAndNormalizedNameContainingOrderByNameAsc(user.getId(), "닭")
            .size());

        List<Meal> meals = mealRepository.findAllByUserIdAndMealDateOrderByEatenAtAscCreatedAtAsc(user.getId(), mealDate);
        assertEquals(1, meals.size());
        assertEquals(1, mealItemRepository.findAllByMealId(meal.getId()).size());

        chicken.archive();
        foodRepository.saveAndFlush(chicken);
        Long chickenId = chicken.getId();
        entityManager.clear();

        Food archivedChicken = foodRepository.findById(chickenId).orElseThrow();
        assertEquals(user.getId(), archivedChicken.getUser().getId());
        assertTrue(archivedChicken.isArchived());
        assertTrue(foodRepository.findByIdAndUserIdAndArchivedFalse(chickenId, user.getId()).isEmpty());
        assertTrue(foodRepository
            .findAllByUserIdAndArchivedFalseAndNormalizedNameContainingOrderByNameAsc(user.getId(), "닭")
            .isEmpty());
    }
}
