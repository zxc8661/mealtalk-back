package com.mealtalk.api.domain.meal.controller;

import com.mealtalk.api.domain.auth.security.AuthenticatedUser;
import com.mealtalk.api.domain.meal.dto.MealListResponse;
import com.mealtalk.api.domain.meal.dto.MealPhotoAction;
import com.mealtalk.api.domain.meal.dto.MealRequest;
import com.mealtalk.api.domain.meal.dto.MealResponse;
import com.mealtalk.api.domain.meal.dto.MealWriteCommand;
import com.mealtalk.api.domain.meal.photo.StoredObject;
import com.mealtalk.api.domain.meal.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The meal journal API.
 *
 * <p>Writes are multipart because a record carries two very different things: a
 * small JSON document the user authored ({@code meal}) and up to one binary
 * photo ({@code photo}). Encoding the image inside the JSON would inflate it by
 * a third and force the whole upload through a string parser.
 *
 * <p>An update must state its intent for the existing photo explicitly. "No file
 * part" cannot be allowed to mean either "keep" or "delete" by inference: one of
 * those two guesses destroys a user's photo, so the client says which it means
 * and every inconsistent combination is refused.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meals")
public class MealController {
    private final MealService mealService;

    @GetMapping
    public MealListResponse list(
        @AuthenticationPrincipal AuthenticatedUser user,
        @RequestParam LocalDate date
    ) {
        return mealService.list(user.userId(), date);
    }

    @GetMapping("/{mealId}")
    public MealResponse get(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long mealId
    ) {
        return mealService.get(user.userId(), mealId);
    }

    /**
     * Streams one photo revision to its owner.
     *
     * <p>Every failure - no photo, a superseded {@code revision}, another user's
     * meal, a deleted meal - produces the identical empty 404. Anything else
     * would let a caller probe which meal ids exist and which of them have a
     * photo. The bytes are private, so the response forbids caching anywhere.
     */
    @GetMapping("/{mealId}/photo")
    public ResponseEntity<byte[]> photo(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long mealId,
        @RequestParam Long revision
    ) {
        Optional<StoredObject> stored = mealService.readPhoto(user.userId(), mealId, revision);
        if (stored.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StoredObject object = stored.get();
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .header("Pragma", "no-cache")
            .contentLength(object.bytes().length)
            .body(object.bytes());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MealResponse> create(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestPart("meal") MealRequest meal,
        @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {
        // A create has nothing to keep, so an action is meaningless here; sending
        // one anyway is a client that has confused its two endpoints.
        if (meal.photoAction() != null) {
            throw new InvalidPhotoActionException("photoAction", "새 기록에는 사진 동작을 지정할 수 없습니다.");
        }
        byte[] bytes = bytesOf(photo);
        MealWriteCommand command = new MealWriteCommand(
            meal,
            bytes,
            bytes == null ? null : MealPhotoAction.REPLACE
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(mealService.create(user.userId(), command));
    }

    @PutMapping(path = "/{mealId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MealResponse update(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long mealId,
        @Valid @RequestPart("meal") MealRequest meal,
        @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {
        MealPhotoAction action = meal.photoAction();
        if (action == null) {
            throw new InvalidPhotoActionException("photoAction", "사진 처리 방식(KEEP, REMOVE, REPLACE)을 지정해주세요.");
        }
        byte[] bytes = bytesOf(photo);
        boolean hasFile = bytes != null;
        if (action == MealPhotoAction.REPLACE && !hasFile) {
            throw new InvalidPhotoActionException("photo", "사진을 교체하려면 새 사진을 함께 보내주세요.");
        }
        if (action != MealPhotoAction.REPLACE && hasFile) {
            throw new InvalidPhotoActionException("photo", "사진을 교체할 때만 사진을 보낼 수 있습니다.");
        }
        return mealService.update(user.userId(), mealId, new MealWriteCommand(meal, bytes, action));
    }

    @DeleteMapping("/{mealId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable Long mealId
    ) {
        mealService.delete(user.userId(), mealId);
        return ResponseEntity.noContent().build();
    }

    /**
     * @return the part's bytes, or {@code null} when the part is absent. An
     *         explicitly sent but empty part is kept as an empty array so the
     *         sanitizer can reject it as EMPTY rather than it being mistaken for
     *         "no photo sent".
     */
    private static byte[] bytesOf(MultipartFile photo) {
        if (photo == null) {
            return null;
        }
        try {
            return photo.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Reading the uploaded photo part failed", exception);
        }
    }

    /** The photo action and the parts sent with it cannot both be true. */
    public static class InvalidPhotoActionException extends RuntimeException {
        private final String field;

        public InvalidPhotoActionException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }
}
