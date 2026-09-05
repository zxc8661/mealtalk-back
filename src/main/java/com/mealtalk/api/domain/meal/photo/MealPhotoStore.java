package com.mealtalk.api.domain.meal.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The single entry point services use for meal photo bytes.
 *
 * <p>Ordering is the whole point: sanitize first, and only write to the bucket
 * once the bytes are proven safe. A rejected upload therefore never produces an
 * object, which is what makes "storage failure leaves no mutation" true rather
 * than merely likely.
 *
 * <h2>Compensating cleanup</h2>
 * Object storage has no transactions, so the caller owns the compensation. The
 * contract is:
 *
 * <pre>{@code
 * StoredMealPhoto stored = store.store(userId, mealId, bytes);
 * try {
 *     // persist the meal_photos row inside the DB transaction
 * } catch (RuntimeException failure) {
 *     store.deleteQuietly(stored.objectKey()); // no orphan object survives
 *     throw failure;
 * }
 * }</pre>
 *
 * When replacing or deleting a photo, commit the database state <em>first</em>
 * and then call {@link #deleteQuietly(String)} on the superseded key: a stale
 * object that outlives its row is recoverable, a row pointing at a deleted
 * object is not.
 *
 * <p>{@link #deleteQuietly(String)} never throws precisely so it cannot mask the
 * original failure that triggered the rollback.
 */
@Service
public class MealPhotoStore {
    private static final Logger log = LoggerFactory.getLogger(MealPhotoStore.class);

    private final MealPhotoStorage storage;
    private final MealPhotoSanitizer sanitizer;

    public MealPhotoStore(MealPhotoStorage storage, MealPhotoSanitizer sanitizer) {
        this.storage = storage;
        this.sanitizer = sanitizer;
    }

    public MealPhotoSanitizer sanitizer() {
        return sanitizer;
    }

    /**
     * Sanitizes {@code uploadedBytes} and writes the result under a fresh opaque key.
     *
     * @throws MealPhotoValidationException when the bytes are not an acceptable photo;
     *                                      nothing is written
     * @throws MealPhotoStorageException    when the bucket rejects the write
     */
    public StoredMealPhoto store(long userId, long mealId, byte[] uploadedBytes) {
        SanitizedMealPhoto sanitized = sanitizer.sanitize(uploadedBytes);
        String objectKey = MealPhotoObjectKeys.generate(userId, mealId);
        storage.put(objectKey, sanitized.jpegBytes(), sanitized.contentType());
        return new StoredMealPhoto(
            objectKey,
            sanitized.contentType(),
            sanitized.byteSize(),
            sanitized.width(),
            sanitized.height(),
            sanitized.checksumSha256()
        );
    }

    /** Reads stored bytes back for an authenticated owner-checked download. */
    public Optional<StoredObject> read(String objectKey) {
        return storage.read(objectKey);
    }

    /**
     * Best-effort removal used for compensation and for superseded objects.
     *
     * <p>Never throws. A failure is logged with its key so the object can be
     * reclaimed later; it must not turn a rolled-back request into a 500.
     */
    public void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            storage.delete(objectKey);
        } catch (MealPhotoStorageException exception) {
            log.warn("사진 오브젝트 정리 실패, 나중에 재시도 필요: key={}", objectKey, exception);
        }
    }
}
