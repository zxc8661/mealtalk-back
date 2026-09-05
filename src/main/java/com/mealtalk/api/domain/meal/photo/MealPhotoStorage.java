package com.mealtalk.api.domain.meal.photo;

import java.util.Optional;

/**
 * The private object store holding sanitized meal photos.
 *
 * <p>Deliberately narrow: the seam knows only opaque keys and bytes, never a
 * {@code MealPhoto} row, a user, or a URL. That keeps the JPA layer and the
 * bucket independent, and it makes the in-memory test double trivially faithful.
 *
 * <p>Every infrastructure failure surfaces as {@link MealPhotoStorageException}
 * so callers can map storage outages to a single HTTP status without catching
 * vendor SDK types.
 */
public interface MealPhotoStorage {
    /**
     * Writes (or overwrites) the object at {@code objectKey}.
     *
     * @throws MealPhotoStorageException when the bucket is unreachable, unconfigured,
     *                                   or rejects the write
     */
    void put(String objectKey, byte[] bytes, String contentType);

    /**
     * Reads the object at {@code objectKey}.
     *
     * @return empty when no such object exists; a missing object is not a failure
     * @throws MealPhotoStorageException when the bucket is unreachable
     */
    Optional<StoredObject> read(String objectKey);

    /**
     * Removes the object at {@code objectKey}. Deleting an absent key succeeds.
     *
     * @throws MealPhotoStorageException when the bucket is unreachable or unconfigured
     */
    void delete(String objectKey);
}
