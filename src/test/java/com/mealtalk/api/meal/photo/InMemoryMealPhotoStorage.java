package com.mealtalk.api.meal.photo;

import com.mealtalk.api.domain.meal.photo.MealPhotoStorage;
import com.mealtalk.api.domain.meal.photo.MealPhotoStorageException;
import com.mealtalk.api.domain.meal.photo.StoredObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic {@link MealPhotoStorage} test double.
 *
 * <p>Holds objects in a map so a test can assert the exact set of keys that
 * reached storage. Never touches the network, so no test needs R2 credentials.
 * Failures are opt-in through {@link #failNextPut()} / {@link #failNextDelete()}.
 */
class InMemoryMealPhotoStorage implements MealPhotoStorage {
    private final Map<String, StoredObject> objects = new LinkedHashMap<>();
    private boolean failPut;
    private boolean failDelete;
    private int putCount;
    private int deleteCount;

    /** Returns the double to a pristine state between tests that share one context. */
    void reset() {
        objects.clear();
        failPut = false;
        failDelete = false;
        putCount = 0;
        deleteCount = 0;
    }

    void failNextPut() {
        this.failPut = true;
    }

    void failNextDelete() {
        this.failDelete = true;
    }

    Set<String> keys() {
        return Collections.unmodifiableSet(objects.keySet());
    }

    boolean isEmpty() {
        return objects.isEmpty();
    }

    int putCount() {
        return putCount;
    }

    int deleteCount() {
        return deleteCount;
    }

    byte[] bytesOf(String objectKey) {
        StoredObject stored = objects.get(objectKey);
        return stored == null ? null : stored.bytes();
    }

    @Override
    public void put(String objectKey, byte[] bytes, String contentType) {
        putCount++;
        if (failPut) {
            failPut = false;
            throw new MealPhotoStorageException("in-memory storage was told to fail this put", null);
        }
        objects.put(objectKey, new StoredObject(objectKey, contentType, bytes.length, bytes.clone()));
    }

    @Override
    public Optional<StoredObject> read(String objectKey) {
        return Optional.ofNullable(objects.get(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        deleteCount++;
        if (failDelete) {
            failDelete = false;
            throw new MealPhotoStorageException("in-memory storage was told to fail this delete", null);
        }
        objects.remove(objectKey);
    }
}
