package com.mealtalk.api.domain.food.controller;

import com.mealtalk.api.domain.auth.security.AuthenticatedUser;
import com.mealtalk.api.domain.food.catalog.FoodCatalogResponse;
import com.mealtalk.api.domain.food.catalog.FoodCatalogService;
import com.mealtalk.api.domain.food.dto.FoodRequest;
import com.mealtalk.api.domain.food.dto.FoodResponse;
import com.mealtalk.api.domain.food.service.FoodService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/foods")
public class FoodController {
    private final FoodService foodService;
    private final FoodCatalogService foodCatalogService;

    @GetMapping
    public List<FoodResponse> list(
        @AuthenticationPrincipal AuthenticatedUser user,
        @RequestParam(required = false) String query
    ) {
        return foodService.list(user.userId(), query);
    }

    @GetMapping("/catalog")
    public List<FoodCatalogResponse> catalog(
        @AuthenticationPrincipal AuthenticatedUser user,
        @RequestParam @NotBlank @Size(max = 100) String query
    ) {
        return foodCatalogService.search(query.trim());
    }

    @GetMapping("/{foodId}")
    public FoodResponse get(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long foodId
    ) {
        return foodService.get(user.userId(), foodId);
    }

    @PostMapping
    public ResponseEntity<FoodResponse> create(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody FoodRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foodService.create(user.userId(), request));
    }

    @PutMapping("/{foodId}")
    public FoodResponse update(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long foodId,
        @Valid @RequestBody FoodRequest request
    ) {
        return foodService.update(user.userId(), foodId, request);
    }

    @DeleteMapping("/{foodId}")
    public ResponseEntity<Void> archive(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long foodId
    ) {
        foodService.archive(user.userId(), foodId);
        return ResponseEntity.noContent().build();
    }
}
