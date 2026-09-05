package com.mealtalk.api.domain.meal.photo;

import java.util.Optional;

/**
 * The storage bean used while the R2 bucket does not exist yet.
 *
 * <p>Its whole job is to keep the application bootable with no {@code R2_*}
 * variables set while still failing honestly: a write attempt raises the same
 * {@link MealPhotoStorageException} an outage would, which callers already map
 * to HTTP 503, instead of crashing at startup or silently pretending to store.
 *
 * <p>A read is an empty result rather than a failure - a bucket that was never
 * configured cannot hold the object, and "not found" is the truthful answer.
 */
public class UnconfiguredMealPhotoStorage implements MealPhotoStorage {
    private static final String MESSAGE =
        "Meal photo storage is not configured; set R2_ENDPOINT, R2_ACCESS_KEY_ID, "
            + "R2_SECRET_ACCESS_KEY and R2_BUCKET in the backend environment";

    @Override
    public void put(String objectKey, byte[] bytes, String contentType) {
        throw new MealPhotoStorageException(MESSAGE, null);
    }

    @Override
    public Optional<StoredObject> read(String objectKey) {
        return Optional.empty();
    }

    @Override
    public void delete(String objectKey) {
        throw new MealPhotoStorageException(MESSAGE, null);
    }
}
