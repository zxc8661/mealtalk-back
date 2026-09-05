package com.mealtalk.api.domain.meal.service;

import com.mealtalk.api.domain.meal.dto.MealListResponse;
import com.mealtalk.api.domain.meal.dto.MealPhotoAction;
import com.mealtalk.api.domain.meal.dto.MealRequest;
import com.mealtalk.api.domain.meal.dto.MealResponse;
import com.mealtalk.api.domain.meal.dto.MealWriteCommand;
import com.mealtalk.api.domain.meal.entity.Meal;
import com.mealtalk.api.domain.meal.entity.MealPhoto;
import com.mealtalk.api.domain.meal.photo.MealPhotoStore;
import com.mealtalk.api.domain.meal.photo.StoredMealPhoto;
import com.mealtalk.api.domain.meal.photo.StoredObject;
import com.mealtalk.api.domain.meal.repository.MealItemRepository;
import com.mealtalk.api.domain.meal.repository.MealPhotoRepository;
import com.mealtalk.api.domain.meal.repository.MealRepository;
import com.mealtalk.api.domain.meal.repository.MealWriteRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Meal records are what the user wrote and shot, so this service stores the
 * normalized memo, the single photo's metadata, and nothing derived. Legacy
 * {@code meal_items} rows are kept as history: they are read by nothing here and
 * only removed together with their meal, which the foreign key requires.
 *
 * <h2>Storage and the database are two systems</h2>
 * The bucket has no transaction, so ordering is the whole safety argument:
 *
 * <ul>
 *   <li><b>Before</b> the database write the object is uploaded. If that fails,
 *       nothing has been mutated at all.</li>
 *   <li>If the database write then fails, the just-uploaded object is deleted so
 *       no orphan survives the rollback.</li>
 *   <li>The object a replace or remove supersedes is deleted <b>after</b> the
 *       commit. A stale object that outlives its row can be reclaimed later; a
 *       row pointing at bytes that are already gone cannot be repaired.</li>
 * </ul>
 *
 * <p>The transactional work therefore lives in {@link MealRecordWriter}: a
 * transaction that spanned the upload could not be rolled back around it, and a
 * self-invoked {@code @Transactional} method would not be transactional at all.
 */
@Service
@RequiredArgsConstructor
public class MealService {
    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final MealPhotoRepository mealPhotoRepository;
    private final MealWriteRequestRepository mealWriteRequestRepository;
    private final MealRecordWriter recordWriter;
    private final MealPhotoStore photoStore;

    @Transactional(readOnly = true)
    public MealListResponse list(Long userId, LocalDate date) {
        List<Meal> meals = mealRepository.findAllByUserIdAndMealDateOrdered(userId, date);
        Map<Long, MealPhoto> photos = photosOf(meals);
        List<MealResponse> responses = meals.stream()
            .map(meal -> MealResponse.from(meal, photos.get(meal.getId())))
            .toList();
        return MealListResponse.from(date, responses);
    }

    @Transactional(readOnly = true)
    public MealResponse get(Long userId, Long mealId) {
        Meal meal = findOwnedMeal(userId, mealId);
        return MealResponse.from(meal, mealPhotoRepository.findByMealId(mealId).orElse(null));
    }

    /**
     * Reads the bytes of one photo revision for its owner.
     *
     * @return empty for every reason a caller must not be able to tell apart:
     *         no photo, a superseded revision, someone else's meal, or a meal
     *         that no longer exists
     */
    @Transactional(readOnly = true)
    public Optional<StoredObject> readPhoto(Long userId, Long mealId, Long revision) {
        return mealRepository.findByIdAndUserId(mealId, userId)
            .flatMap(meal -> mealPhotoRepository.findByMealId(meal.getId()))
            .filter(photo -> photo.getId().equals(revision))
            .flatMap(photo -> photoStore.read(photo.getObjectKey()));
    }

    public MealResponse create(Long userId, MealWriteCommand command) {
        MealRequest request = command.request();

        MealResponse replayed = replayIfAlreadyCreated(userId, request);
        if (replayed != null) {
            return replayed;
        }

        requireNonEmptyRecord(request.normalizedMemo(), command.hasPhotoUpload());

        if (!command.hasPhotoUpload()) {
            return recordWriter.create(userId, request, null, fingerprintOf(request));
        }

        // A brand new record has no id yet; the key only has to be opaque and
        // unique, and it is persisted with the row that owns it.
        StoredMealPhoto stored = photoStore.store(userId, 0L, command.photoBytes());
        try {
            return recordWriter.create(userId, request, stored, fingerprintOf(request));
        } catch (RuntimeException failure) {
            // The transaction rolled back, so the object nothing references must go.
            photoStore.deleteQuietly(stored.objectKey());
            throw failure;
        }
    }

    public MealResponse update(Long userId, Long mealId, MealWriteCommand command) {
        MealRequest request = command.request();
        MealPhotoAction action = command.action();

        // Ownership is checked before anything is uploaded: a foreign meal must
        // never cause a write to the bucket.
        MealPhoto existing = recordWriter.currentPhotoOfOwnedMeal(userId, mealId);

        boolean resultHasPhoto = switch (action) {
            case KEEP -> existing != null;
            case REMOVE -> false;
            case REPLACE -> true;
        };
        requireNonEmptyRecord(request.normalizedMemo(), resultHasPhoto);

        String supersededKey = existing == null ? null : existing.getObjectKey();

        if (action == MealPhotoAction.KEEP) {
            return recordWriter.update(userId, mealId, request, null, MealPhotoAction.KEEP);
        }
        if (action == MealPhotoAction.REMOVE) {
            MealResponse response = recordWriter.update(userId, mealId, request, null, MealPhotoAction.REMOVE);
            photoStore.deleteQuietly(supersededKey);
            return response;
        }

        StoredMealPhoto stored = photoStore.store(userId, mealId, command.photoBytes());
        MealResponse response;
        try {
            response = recordWriter.update(userId, mealId, request, stored, MealPhotoAction.REPLACE);
        } catch (RuntimeException failure) {
            photoStore.deleteQuietly(stored.objectKey());
            throw failure;
        }
        // Only now that the row points at the new key is the old object expendable.
        photoStore.deleteQuietly(supersededKey);
        return response;
    }

    public void delete(Long userId, Long mealId) {
        String objectKey = recordWriter.delete(userId, mealId);
        // The row is gone either way; a surviving object is reclaimable, so this
        // best-effort delete runs after the commit and never fails the request.
        photoStore.deleteQuietly(objectKey);
    }

    // --------------------------------------------------------------- internals

    private MealResponse replayIfAlreadyCreated(Long userId, MealRequest request) {
        String requestId = requestIdOf(request);
        if (requestId == null) {
            return null;
        }
        return recordWriter.replay(userId, requestId, fingerprintOf(request));
    }

    private Map<Long, MealPhoto> photosOf(List<Meal> meals) {
        if (meals.isEmpty()) {
            return Map.of();
        }
        List<Long> mealIds = meals.stream().map(Meal::getId).toList();
        Map<Long, MealPhoto> byMeal = new LinkedHashMap<>();
        for (MealPhoto photo : mealPhotoRepository.findAllByMealIdIn(mealIds)) {
            byMeal.put(photo.getMeal().getId(), photo);
        }
        return byMeal;
    }

    /** The record invariant: a record must carry something the user wrote or shot. */
    private static void requireNonEmptyRecord(String normalizedMemo, boolean resultHasPhoto) {
        if (normalizedMemo == null && !resultHasPhoto) {
            throw new EmptyMealRecordException();
        }
    }

    static String requestIdOf(MealRequest request) {
        var parsed = request.parsedClientRequestId();
        return parsed == null ? null : parsed.toString();
    }

    /**
     * A stable hash of everything the client authored. The photo bytes are not
     * part of it: a retry re-sends the same draft, and re-hashing megabytes to
     * decide whether to skip work would cost more than the work.
     */
    private static String fingerprintOf(MealRequest request) {
        String canonical = String.join("\u001F",
            String.valueOf(request.mealDate()),
            String.valueOf(request.mealType()),
            String.valueOf(request.eatenAt()),
            String.valueOf(request.normalizedMemo())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every JVM", exception);
        }
    }

    private Meal findOwnedMeal(Long userId, Long mealId) {
        return mealRepository.findByIdAndUserId(mealId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
    }

    /** The record would end up with neither a memo nor a photo. */
    public static class EmptyMealRecordException extends RuntimeException {
        public EmptyMealRecordException() {
            super("A meal record needs a memo or a photo");
        }
    }
}
