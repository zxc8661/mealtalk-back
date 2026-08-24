package com.mealtalk.api.domain;

import com.mealtalk.api.domain.chat.entity.ChatMessage;
import com.mealtalk.api.domain.chat.entity.ChatRole;
import com.mealtalk.api.domain.chat.entity.ChatRoom;
import com.mealtalk.api.domain.chat.entity.MessageStatus;
import com.mealtalk.api.domain.chat.repository.ChatMessageRepository;
import com.mealtalk.api.domain.chat.repository.ChatRoomRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class DomainRelationshipRepositoryTests {
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserTargetRepository userTargetRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private FoodRepository foodRepository;
    @Autowired private MealRepository mealRepository;
    @Autowired private MealItemRepository mealItemRepository;

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

        ChatRoom room = chatRoomRepository.save(ChatRoom.create(user));
        chatMessageRepository.save(ChatMessage.create(
            room,
            ChatRole.USER,
            "점심에 닭가슴살 200g이랑 밥 180g 먹었어",
            "ADD_MEAL",
            Map.of("meal_type", "LUNCH"),
            MessageStatus.COMPLETED
        ));

        Food chicken = foodRepository.save(Food.create(
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
        assertTrue(chatRoomRepository.findByUserId(user.getId()).isPresent());
        assertEquals(1, chatMessageRepository.findAllByRoomIdOrderByCreatedAtAsc(room.getId()).size());
        assertTrue(foodRepository.findByExternalSourceAndExternalFoodId("fixture", "chicken-breast").isPresent());

        List<Meal> meals = mealRepository.findAllByUserIdAndMealDateOrderByEatenAtAscCreatedAtAsc(user.getId(), mealDate);
        assertEquals(1, meals.size());
        assertEquals(1, mealItemRepository.findAllByMealId(meal.getId()).size());
    }
}
