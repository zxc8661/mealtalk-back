package com.mealtalk.api.domain.food.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodCatalogService {
    private final FoodSafetyKoreaCatalogClient foodSafetyKoreaCatalogClient;

    public List<FoodCatalogResponse> search(String query) {
        return foodSafetyKoreaCatalogClient.search(query.trim());
    }
}
