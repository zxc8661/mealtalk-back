package com.mealtalk.api.domain.meal.controller;

import com.mealtalk.api.domain.auth.security.AuthenticatedUser;
import com.mealtalk.api.domain.meal.dto.MealListResponse;
import com.mealtalk.api.domain.meal.dto.MealRequest;
import com.mealtalk.api.domain.meal.dto.MealResponse;
import com.mealtalk.api.domain.meal.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meals")
public class MealController {
    private final MealService mealService;

    @GetMapping
    public MealListResponse list(
        @AuthenticationPrincipal AuthenticatedUser user,
        @RequestParam LocalDate date
    ) {
        return mealService.list(user.userId(), date);
    }

    @GetMapping("/{mealId}")
    public MealResponse get(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long mealId
    ) {
        return mealService.get(user.userId(), mealId);
    }

    @PostMapping
    public ResponseEntity<MealResponse> create(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody MealRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mealService.create(user.userId(), request));
    }

    @PutMapping("/{mealId}")
    public MealResponse update(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long mealId,
        @Valid @RequestBody MealRequest request
    ) {
        return mealService.update(user.userId(), mealId, request);
    }

    @DeleteMapping("/{mealId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long mealId
    ) {
        mealService.delete(user.userId(), mealId);
        return ResponseEntity.noContent().build();
    }
}
