package com.mealtalk.api.domain.meal.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-process photo storage for the {@code e2e} fixture profile only.
 *
 * <p>Manual QA has to exercise the real HTTP surface - upload, stream, replace,
 * remove - and the R2 bucket does not exist yet. Pointing a throwaway QA server
 * at real credentials to prove the API works would be both impossible today and
 * wrong tomorrow, so the profile that already replaces Google token verification
 * with a fixture replaces the bucket with a map too.
 *
 * <p>Bytes live in heap and die with the process. This bean is unreachable
 * without {@code spring.profiles.active=e2e}; production wiring is decided by
 * {@link MealPhotoStorageConfig} from the {@code R2_*} variables as before.
 *
 * <p>{@code mealtalk.e2e.photo-storage-fail=true} makes every write and delete
 * raise {@link MealPhotoStorageException}, which is how a storage outage is
 * demonstrated on the real surface without breaking anything.
 */
@Component
@Primary
@Profile("e2e")
public class E2eMealPhotoStorage implements MealPhotoStorage {
    private final Map<String, StoredObject> objects = new LinkedHashMap<>();
    private final boolean alwaysFail;

    public E2eMealPhotoStorage(@Value("${mealtalk.e2e.photo-storage-fail:false}") boolean alwaysFail) {
        this.alwaysFail = alwaysFail;
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(objects.keySet());
    }

    @Override
    public synchronized void put(String objectKey, byte[] bytes, String contentType) {
        failIfConfiguredTo();
        objects.put(objectKey, new StoredObject(objectKey, contentType, bytes.length, bytes.clone()));
    }

    @Override
    public synchronized Optional<StoredObject> read(String objectKey) {
        return Optional.ofNullable(objects.get(objectKey));
    }

    @Override
    public synchronized void delete(String objectKey) {
        failIfConfiguredTo();
        objects.remove(objectKey);
    }

    private void failIfConfiguredTo() {
        if (alwaysFail) {
            throw new MealPhotoStorageException("E2E fixture storage is configured to fail every write", null);
        }
    }
}
