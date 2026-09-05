package com.mealtalk.api.meal.photo;

import com.mealtalk.api.domain.meal.photo.MealPhotoStorage;
import com.mealtalk.api.domain.meal.photo.MealPhotoStorageException;
import com.mealtalk.api.domain.meal.photo.MealPhotoStorageProperties;
import com.mealtalk.api.domain.meal.photo.UnconfiguredMealPhotoStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The user has not created the R2 bucket yet, so the application must boot and
 * behave predictably with every {@code R2_*} variable absent, and must switch to
 * the real client the moment those values appear in {@code .env}.
 */
class MealPhotoStorageConfigurationTests {
    @Test
    @DisplayName("Absent R2 variables leave the properties incomplete rather than crashing")
    void absentVariablesAreNotConfigured() {
        MealPhotoStorageProperties properties = new MealPhotoStorageProperties(null, null, null, null, null);

        assertThat(properties.isConfigured())
            .as("no endpoint, keys or bucket means storage is simply not configured")
            .isFalse();
        assertThat(properties.region()).as("region falls back to R2's 'auto'").isEqualTo("auto");
    }

    @Test
    @DisplayName("Blank R2 variables count as absent")
    void blankVariablesCountAsAbsent() {
        MealPhotoStorageProperties properties = new MealPhotoStorageProperties("", "  ", "", "bucket", "");

        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.region()).isEqualTo("auto");
    }

    @Test
    @DisplayName("A complete set of R2 variables reports as configured")
    void completeVariablesAreConfigured() {
        MealPhotoStorageProperties properties = new MealPhotoStorageProperties(
            "https://account.r2.cloudflarestorage.com",
            "access-key",
            "secret-key",
            "meal-photos",
            null
        );

        assertThat(properties.isConfigured())
            .as("endpoint + both keys + bucket is everything the S3 client needs")
            .isTrue();
        assertThat(properties.region()).isEqualTo("auto");
    }

    @Test
    @DisplayName("Writing a photo without configuration fails typed, not with a startup crash")
    void unconfiguredStorageFailsTypedOnUse() {
        MealPhotoStorage storage = new UnconfiguredMealPhotoStorage();

        assertThatThrownBy(() -> storage.put("meals/x/1/y.jpg", new byte[] {1, 2, 3}, "image/jpeg"))
            .as("an unconfigured bucket must surface as a storage failure todo 3 can map to 503")
            .isInstanceOf(MealPhotoStorageException.class)
            .hasMessageContaining("not configured");

        assertThatThrownBy(() -> storage.delete("meals/x/1/y.jpg"))
            .isInstanceOf(MealPhotoStorageException.class);
    }

    @Test
    @DisplayName("Reading from unconfigured storage is an empty result, not an exception")
    void unconfiguredStorageReadsEmpty() {
        MealPhotoStorage storage = new UnconfiguredMealPhotoStorage();

        assertThat(storage.read("meals/x/1/y.jpg"))
            .as("a read of a bucket that cannot exist yet is simply a miss")
            .isEmpty();
    }
}
