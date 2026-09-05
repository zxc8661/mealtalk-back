package com.mealtalk.api.meal.photo;

import com.mealtalk.api.domain.meal.photo.MealPhotoStorage;
import com.mealtalk.api.domain.meal.photo.MealPhotoStorageConfig;
import com.mealtalk.api.domain.meal.photo.MealPhotoStorageProperties;
import com.mealtalk.api.domain.meal.photo.R2MealPhotoStorage;
import com.mealtalk.api.domain.meal.photo.UnconfiguredMealPhotoStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the bean choice flips on configuration alone, so pasting real values
 * into {@code .env} is genuinely the only step needed to go live.
 *
 * <p>Building the client does not open a connection, so this stays offline.
 */
class MealPhotoStorageWiringTests {
    private final MealPhotoStorageConfig config = new MealPhotoStorageConfig();

    @Test
    @DisplayName("With no R2 variables the inactive storage bean is wired")
    void wiresUnconfiguredStorageWhenAbsent() {
        MealPhotoStorage storage = config.mealPhotoStorage(
            new MealPhotoStorageProperties(null, null, null, null, null)
        );

        assertThat(storage)
            .as("the application must still start with the bucket missing")
            .isInstanceOf(UnconfiguredMealPhotoStorage.class);
    }

    @Test
    @DisplayName("With R2 variables present the real R2 storage bean is wired")
    void wiresR2StorageWhenConfigured() {
        MealPhotoStorage storage = config.mealPhotoStorage(new MealPhotoStorageProperties(
            "https://example-account.r2.cloudflarestorage.com",
            "test-access-key-id",
            "test-secret-access-key",
            "test-bucket",
            null
        ));

        assertThat(storage)
            .as("filling in .env must switch to the real client with no code change")
            .isInstanceOf(R2MealPhotoStorage.class);
    }
}
