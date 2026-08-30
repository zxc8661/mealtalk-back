package com.mealtalk.api.domain;

import com.mealtalk.api.domain.food.entity.Food;
import com.mealtalk.api.domain.food.repository.FoodRepository;
import com.mealtalk.api.domain.meal.entity.Meal;
import com.mealtalk.api.domain.meal.entity.MealItem;
import com.mealtalk.api.domain.meal.entity.MealType;
import com.mealtalk.api.domain.meal.repository.MealItemRepository;
import com.mealtalk.api.domain.meal.repository.MealRepository;
import com.mealtalk.api.domain.user.entity.AuthProvider;
import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class MealItemSnapshotPersistenceTests {
    @Autowired private UserRepository userRepository;
    @Autowired private FoodRepository foodRepository;
    @Autowired private MealRepository mealRepository;
    @Autowired private MealItemRepository mealItemRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void mealItemKeepsItsNameUnitAndNutritionSnapshotsAfterFoodChanges() {
        User user = userRepository.save(User.create(
            AuthProvider.GOOGLE,
            "snapshot-user",
            "snapshot@example.com",
            "Snapshot user",
            "UTC"
        ));
        Food food = foodRepository.save(Food.create(
            user,
            "Original oats",
            "original oats",
            new BigDecimal("100.000"),
            "g",
            new BigDecimal("389.000"),
            new BigDecimal("66.300"),
            new BigDecimal("16.900"),
            new BigDecimal("6.900"),
            null,
            null,
            null
        ));
        Meal meal = mealRepository.save(Meal.create(
            user,
            LocalDate.of(2026, 8, 29),
            MealType.BREAKFAST,
            null,
            null
        ));
        MealItem item = mealItemRepository.saveAndFlush(MealItem.create(
            meal,
            food,
            new BigDecimal("200.000"),
            "serving",
            new BigDecimal("778.000"),
            new BigDecimal("132.600"),
            new BigDecimal("33.800"),
            new BigDecimal("13.800")
        ));

        food.update(
            "Renamed oats",
            new BigDecimal("50.000"),
            "cup",
            new BigDecimal("200.000"),
            new BigDecimal("40.000"),
            new BigDecimal("8.000"),
            new BigDecimal("4.000")
        );
        food.archive();
        foodRepository.saveAndFlush(food);
        entityManager.clear();

        MealItem stored = mealItemRepository.findById(item.getId()).orElseThrow();
        assertEquals("Original oats", stored.getFoodName());
        assertEquals("serving", stored.getUnit());
        assertDecimalEquals("778.000", stored.getCaloriesKcal());
        assertDecimalEquals("132.600", stored.getCarbohydratesG());
        assertDecimalEquals("33.800", stored.getProteinG());
        assertDecimalEquals("13.800", stored.getFatG());
        assertTrue(stored.getFood().isArchived());
        assertEquals("Renamed oats", stored.getFood().getName());
    }

    private static void assertDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, actual.compareTo(new BigDecimal(expected)));
    }
}
