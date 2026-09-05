package com.mealtalk.api.domain.meal.service;

import com.mealtalk.api.domain.meal.dto.MealPhotoAction;
import com.mealtalk.api.domain.meal.dto.MealRequest;
import com.mealtalk.api.domain.meal.dto.MealResponse;
import com.mealtalk.api.domain.meal.entity.Meal;
import com.mealtalk.api.domain.meal.entity.MealPhoto;
import com.mealtalk.api.domain.meal.entity.MealWriteRequest;
import com.mealtalk.api.domain.meal.photo.StoredMealPhoto;
import com.mealtalk.api.domain.meal.repository.MealItemRepository;
import com.mealtalk.api.domain.meal.repository.MealPhotoRepository;
import com.mealtalk.api.domain.meal.repository.MealRepository;
import com.mealtalk.api.domain.meal.repository.MealWriteRequestRepository;
import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The database half of a meal write, isolated in its own bean on purpose.
 *
 * <p>{@link MealService} has to upload before the transaction and compensate
 * after it fails, so the transaction cannot span the whole method. Spring's
 * {@code @Transactional} is proxy-based and a self-call would silently run
 * outside any transaction, which is exactly the bug that would make "a failed
 * database write leaves no row" untrue while every test still passed. Crossing a
 * real bean boundary is what makes the rollback real.
 */
@Component
@RequiredArgsConstructor
public class MealRecordWriter {
    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final MealPhotoRepository mealPhotoRepository;
    private final MealWriteRequestRepository mealWriteRequestRepository;
    private final UserRepository userRepository;

    /**
     * Removes the record and everything that hangs off it.
     *
     * @return the object key the caller must clean up after this commit, or
     *         {@code null} when the record had no photo
     */
    @Transactional
    public String delete(Long userId, Long mealId) {
        Meal meal = mealRepository.findByIdAndUserId(mealId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        String objectKey = mealPhotoRepository.findByMealId(mealId)
            .map(MealPhoto::getObjectKey)
            .orElse(null);
        mealWriteRequestRepository.deleteAllByMealId(mealId);
        mealPhotoRepository.deleteByMealId(mealId);
        mealItemRepository.deleteAllByMealId(mealId);
        mealRepository.delete(meal);
        return objectKey;
    }

    /**
     * @return the record this request id already produced, or {@code null} when
     *         the id is new
     * @throws ResponseStatusException 409 when the id was used for a different payload
     */
    @Transactional(readOnly = true)
    public MealResponse replay(Long userId, String clientRequestId, String fingerprint) {
        return mealWriteRequestRepository.findByUserIdAndClientRequestId(userId, clientRequestId)
            .map(previous -> {
                if (!previous.getFingerprint().equals(fingerprint)) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Client request id reused for a different meal payload"
                    );
                }
                Meal meal = previous.getMeal();
                return MealResponse.from(meal, mealPhotoRepository.findByMealId(meal.getId()).orElse(null));
            })
            .orElse(null);
    }

    /**
     * Owner check plus the record's current photo, resolved in one transaction so
     * the caller can decide what to upload before any transaction is opened for
     * the write itself.
     *
     * @throws ResponseStatusException 404 when the meal is absent or not the caller's
     */
    @Transactional(readOnly = true)
    public MealPhoto currentPhotoOfOwnedMeal(Long userId, Long mealId) {
        Meal meal = mealRepository.findByIdAndUserId(mealId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        return mealPhotoRepository.findByMealId(meal.getId()).orElse(null);
    }

    @Transactional
    public MealResponse create(Long userId, MealRequest request, StoredMealPhoto stored, String fingerprint) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        Meal meal = mealRepository.save(Meal.create(
            user,
            request.mealDate(),
            request.mealType(),
            request.eatenAt(),
            request.normalizedMemo()
        ));

        MealPhoto photo = stored == null ? null : mealPhotoRepository.save(newPhoto(meal, stored));

        String requestId = MealService.requestIdOf(request);
        if (requestId != null) {
            mealWriteRequestRepository.save(MealWriteRequest.create(user, requestId, fingerprint, meal));
        }
        return MealResponse.from(meal, photo);
    }

    @Transactional
    public MealResponse update(
        Long userId,
        Long mealId,
        MealRequest request,
        StoredMealPhoto stored,
        MealPhotoAction action
    ) {
        Meal meal = mealRepository.findByIdAndUserId(mealId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        meal.update(request.mealDate(), request.mealType(), request.eatenAt(), request.normalizedMemo());

        MealPhoto photo = mealPhotoRepository.findByMealId(mealId).orElse(null);
        switch (action) {
            case KEEP -> {
                // The row already points at the object being kept.
            }
            case REMOVE -> {
                if (photo != null) {
                    mealPhotoRepository.delete(photo);
                    photo = null;
                }
            }
            case REPLACE -> {
                if (photo != null) {
                    // Deleted and re-inserted rather than mutated, so the photo id
                    // is a genuine revision: a client holding the previous id gets
                    // a 404 instead of different bytes at the same address.
                    mealPhotoRepository.delete(photo);
                    mealPhotoRepository.flush();
                }
                photo = mealPhotoRepository.save(newPhoto(meal, stored));
            }
        }
        return MealResponse.from(meal, photo);
    }

    private static MealPhoto newPhoto(Meal meal, StoredMealPhoto stored) {
        return MealPhoto.create(
            meal,
            stored.objectKey(),
            stored.contentType(),
            stored.byteSize(),
            stored.width(),
            stored.height(),
            stored.checksumSha256()
        );
    }
}
