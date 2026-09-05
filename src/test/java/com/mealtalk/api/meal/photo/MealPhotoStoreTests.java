package com.mealtalk.api.meal.photo;

import com.mealtalk.api.domain.meal.photo.MealPhotoObjectKeys;
import com.mealtalk.api.domain.meal.photo.MealPhotoStorageException;
import com.mealtalk.api.domain.meal.photo.MealPhotoStore;
import com.mealtalk.api.domain.meal.photo.MealPhotoValidationException;
import com.mealtalk.api.domain.meal.photo.SanitizedMealPhoto;
import com.mealtalk.api.domain.meal.photo.StoredMealPhoto;
import com.mealtalk.api.domain.meal.photo.StoredObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The store is the seam todo 3 calls: sanitize, then write to the private
 * bucket, with an explicit compensation hook for a failed DB transaction.
 */
class MealPhotoStoreTests {
    private final InMemoryMealPhotoStorage storage = new InMemoryMealPhotoStorage();
    private final MealPhotoStore store = new MealPhotoStore(storage, new com.mealtalk.api.domain.meal.photo.MealPhotoSanitizer());

    @Test
    @DisplayName("A valid PNG is sanitized and written under an opaque server-generated key")
    void storesSanitizedJpegUnderOpaqueKey() {
        StoredMealPhoto stored = store.store(42L, 7L, MealPhotoFixtures.png(3000, 1500));

        assertThat(storage.keys()).as("exactly one object must reach storage").hasSize(1);
        assertThat(stored.objectKey())
            .as("key must be namespaced per user and meal and end with .jpg")
            .startsWith("meals/")
            .contains("/7/")
            .endsWith(".jpg");
        assertThat(storage.keys()).containsExactly(stored.objectKey());
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.width()).isEqualTo(2048);
        assertThat(stored.height()).isEqualTo(1024);
        assertThat(stored.byteSize()).isEqualTo(storage.bytesOf(stored.objectKey()).length);
        assertThat(stored.checksumSha256()).matches("[0-9a-f]{64}");

        Optional<StoredObject> readBack = storage.read(stored.objectKey());
        assertThat(readBack).as("the written object must be readable through the same seam").isPresent();
        assertThat(readBack.get().contentType()).isEqualTo("image/jpeg");
        assertThat(readBack.get().bytes()).isEqualTo(store.sanitizer().sanitize(MealPhotoFixtures.png(3000, 1500)).jpegBytes());
    }

    @Test
    @DisplayName("Object keys are unguessable and unique per write")
    void generatesUnguessableUniqueKeys() {
        String first = MealPhotoObjectKeys.generate(42L, 7L);
        String second = MealPhotoObjectKeys.generate(42L, 7L);

        assertThat(first).as("two writes for the same meal must never collide").isNotEqualTo(second);
        assertThat(first)
            .as("the raw user id must not appear in the key path segment")
            .doesNotContain("/42/");
        assertThat(first).matches("meals/[0-9a-f]{16,}/7/[0-9a-f-]{36}\\.jpg");
    }

    @Test
    @DisplayName("An oversized-dimension input is rejected before any storage call")
    void rejectsDecompressionBombBeforeStorage() {
        assertThatThrownBy(() -> store.store(42L, 7L, MealPhotoFixtures.decompressionBombPng(8000, 6000)))
            .isInstanceOf(MealPhotoValidationException.class);

        assertThat(storage.isEmpty()).as("nothing may be written when validation fails").isTrue();
        assertThat(storage.putCount()).as("storage.put must not even be attempted").isZero();
    }

    @Test
    @DisplayName("An oversized-byte input is rejected before any storage call")
    void rejectsOversizedBytesBeforeStorage() {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;

        assertThatThrownBy(() -> store.store(42L, 7L, oversized))
            .isInstanceOf(MealPhotoValidationException.class);

        assertThat(storage.isEmpty()).isTrue();
        assertThat(storage.putCount()).isZero();
    }

    @Test
    @DisplayName("Corrupt, empty, SVG and animated payloads are all rejected with storage untouched")
    void rejectsMalformedPayloadsWithoutTouchingStorage() {
        assertThatThrownBy(() -> store.store(42L, 7L, MealPhotoFixtures.corrupt()))
            .isInstanceOf(MealPhotoValidationException.class);
        assertThatThrownBy(() -> store.store(42L, 7L, new byte[0]))
            .isInstanceOf(MealPhotoValidationException.class);
        assertThatThrownBy(() -> store.store(42L, 7L, MealPhotoFixtures.svg()))
            .isInstanceOf(MealPhotoValidationException.class);
        assertThatThrownBy(() -> store.store(42L, 7L, MealPhotoFixtures.animatedGif(48, 48)))
            .isInstanceOf(MealPhotoValidationException.class);

        assertThat(storage.keys()).as("no rejected payload may leave an object behind").isEmpty();
        assertThat(storage.putCount()).isZero();
    }

    @Test
    @DisplayName("A storage failure surfaces the typed storage exception and leaves no object")
    void surfacesStorageFailureWithoutMutation() {
        storage.failNextPut();

        assertThatThrownBy(() -> store.store(42L, 7L, MealPhotoFixtures.png(600, 400)))
            .isInstanceOf(MealPhotoStorageException.class);

        assertThat(storage.isEmpty()).as("a failed put must leave the bucket unchanged").isTrue();
    }

    @Test
    @DisplayName("Compensating delete removes an object whose transaction did not commit")
    void compensatingDeleteRemovesUncommittedObject() {
        StoredMealPhoto stored = store.store(42L, 7L, MealPhotoFixtures.png(600, 400));
        assertThat(storage.keys()).hasSize(1);

        store.deleteQuietly(stored.objectKey());

        assertThat(storage.isEmpty())
            .as("compensation must remove the orphan object left by a rolled-back write")
            .isTrue();
    }

    @Test
    @DisplayName("Compensating delete never throws, so it cannot mask the original failure")
    void compensatingDeleteSwallowsStorageFailure() {
        StoredMealPhoto stored = store.store(42L, 7L, MealPhotoFixtures.png(600, 400));
        storage.failNextDelete();

        store.deleteQuietly(stored.objectKey());

        assertThat(storage.deleteCount()).as("the delete must still have been attempted").isEqualTo(1);
        assertThat(storage.keys())
            .as("a failed best-effort delete leaves the object for later cleanup, not an exception")
            .containsExactly(stored.objectKey());
    }
}
