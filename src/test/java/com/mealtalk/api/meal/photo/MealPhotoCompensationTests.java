package com.mealtalk.api.meal.photo;

import com.mealtalk.api.domain.meal.dto.MealPhotoAction;
import com.mealtalk.api.domain.meal.dto.MealRequest;
import com.mealtalk.api.domain.meal.dto.MealWriteCommand;
import com.mealtalk.api.domain.meal.entity.MealType;
import com.mealtalk.api.domain.meal.photo.MealPhotoSanitizer;
import com.mealtalk.api.domain.meal.photo.MealPhotoStore;
import com.mealtalk.api.domain.meal.photo.StoredMealPhoto;
import com.mealtalk.api.domain.meal.repository.MealItemRepository;
import com.mealtalk.api.domain.meal.repository.MealPhotoRepository;
import com.mealtalk.api.domain.meal.repository.MealRepository;
import com.mealtalk.api.domain.meal.repository.MealWriteRequestRepository;
import com.mealtalk.api.domain.meal.service.MealRecordWriter;
import com.mealtalk.api.domain.meal.service.MealService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The half of the compensation contract an HTTP test cannot reach: the object
 * write succeeds and the database write then fails. The service owes the bucket
 * a best-effort delete so no orphan object outlives the rolled-back transaction.
 *
 * <p>The database side is a mock precisely because a real failure at that exact
 * point is not reproducible on demand; what is under test here is the service's
 * ordering and its compensation, not JPA.
 */
class MealPhotoCompensationTests {
    private final InMemoryMealPhotoStorage storage = new InMemoryMealPhotoStorage();
    private final MealPhotoStore store = new MealPhotoStore(storage, new MealPhotoSanitizer());
    private final MealRecordWriter recordWriter = mock(MealRecordWriter.class);
    private final MealService service = new MealService(
        mock(MealRepository.class),
        mock(MealItemRepository.class),
        mock(MealPhotoRepository.class),
        mock(MealWriteRequestRepository.class),
        recordWriter,
        store
    );

    private static final MealWriteCommand PHOTO_CREATE = new MealWriteCommand(
        new MealRequest(LocalDate.of(2026, 8, 29), MealType.LUNCH, null, "보상 테스트", null, null),
        MealPhotoFixtures.png(400, 300),
        MealPhotoAction.REPLACE
    );

    @Test
    @DisplayName("A failed database write after a successful upload deletes the orphan object")
    void deletesOrphanObjectWhenTheDatabaseWriteFails() {
        when(recordWriter.create(eq(1L), any(MealRequest.class), any(StoredMealPhoto.class), any()))
            .thenThrow(new IllegalStateException("database refused the meal insert"));

        assertThatThrownBy(() -> service.create(1L, PHOTO_CREATE))
            .as("the original database failure must surface, not a masked cleanup error")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("database refused");

        assertThat(storage.putCount()).as("the upload happened before the failed write").isEqualTo(1);
        assertThat(storage.keys())
            .as("compensation must remove the object the rolled-back transaction left behind")
            .isEmpty();
        assertThat(storage.deleteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A cleanup failure never masks the original database failure")
    void cleanupFailureDoesNotMaskTheOriginalFailure() {
        when(recordWriter.create(eq(1L), any(MealRequest.class), any(StoredMealPhoto.class), any()))
            .thenThrow(new IllegalStateException("database refused the meal insert"));
        storage.failNextDelete();

        assertThatThrownBy(() -> service.create(1L, PHOTO_CREATE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("database refused");

        assertThat(storage.deleteCount()).as("cleanup must still have been attempted").isEqualTo(1);
        assertThat(storage.keys())
            .as("a failed best-effort delete leaves the object for later reclamation")
            .hasSize(1);
    }

    @Test
    @DisplayName("A failed REPLACE keeps the superseded object and deletes only the new one")
    void failedReplaceKeepsTheSupersededObject() {
        StoredMealPhoto original = store.store(1L, 7L, MealPhotoFixtures.png(300, 200));
        String originalKey = original.objectKey();

        com.mealtalk.api.domain.meal.entity.MealPhoto existing =
            com.mealtalk.api.domain.meal.entity.MealPhoto.create(
                null, originalKey, "image/jpeg", original.byteSize(),
                original.width(), original.height(), original.checksumSha256()
            );
        when(recordWriter.currentPhotoOfOwnedMeal(1L, 7L)).thenReturn(existing);
        when(recordWriter.update(eq(1L), eq(7L), any(MealRequest.class), any(StoredMealPhoto.class),
            eq(MealPhotoAction.REPLACE)))
            .thenThrow(new IllegalStateException("database refused the photo swap"));

        MealWriteCommand replace = new MealWriteCommand(
            new MealRequest(LocalDate.of(2026, 8, 29), MealType.LUNCH, null, "교체", MealPhotoAction.REPLACE, null),
            MealPhotoFixtures.png(500, 400),
            MealPhotoAction.REPLACE
        );

        assertThatThrownBy(() -> service.update(1L, 7L, replace))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("database refused");

        assertThat(storage.keys())
            .as("the record still points at the original object, so only the new one may be removed")
            .containsExactly(originalKey);
    }

    @Test
    @DisplayName("A KEEP update never uploads and never deletes")
    void keepTouchesNoObject() {
        StoredMealPhoto original = store.store(1L, 7L, MealPhotoFixtures.png(300, 200));
        com.mealtalk.api.domain.meal.entity.MealPhoto existing =
            com.mealtalk.api.domain.meal.entity.MealPhoto.create(
                null, original.objectKey(), "image/jpeg", original.byteSize(),
                original.width(), original.height(), original.checksumSha256()
            );
        when(recordWriter.currentPhotoOfOwnedMeal(anyLong(), anyLong())).thenReturn(existing);
        when(recordWriter.update(anyLong(), anyLong(), any(MealRequest.class), isNull(), eq(MealPhotoAction.KEEP)))
            .thenReturn(null);

        MealWriteCommand keep = new MealWriteCommand(
            new MealRequest(LocalDate.of(2026, 8, 29), MealType.LUNCH, null, "유지", MealPhotoAction.KEEP, null),
            null,
            MealPhotoAction.KEEP
        );
        service.update(1L, 7L, keep);

        assertThat(storage.putCount()).as("KEEP must not upload").isEqualTo(1);
        assertThat(storage.deleteCount()).as("KEEP must not delete").isZero();
        assertThat(storage.keys()).containsExactly(original.objectKey());
    }
}
