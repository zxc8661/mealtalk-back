package com.mealtalk.api.domain.meal.dto;

import com.mealtalk.api.domain.meal.entity.MealPhoto;

/**
 * The safe half of a stored photo.
 *
 * <p>Everything that identifies the object inside the private bucket - the
 * object key, the bucket name, the R2 endpoint, any credential or signed URL -
 * is deliberately absent. What the client gets is {@link #url}, the
 * authenticated API path it must call with its own bearer token; the bytes never
 * become reachable without that token.
 *
 * <p>{@link #id} doubles as the revision: it changes on every replace, so a
 * client that cached the previous image gets a 404 rather than stale bytes, and
 * a stale link cannot resurrect a superseded photo.
 *
 * <p>{@link #checksumSha256} is exposed on purpose. It is a hash of bytes the
 * owner already possesses, so it leaks nothing they cannot compute themselves,
 * and it is what lets a later AI analysis service (and the client's own cache)
 * recognise unchanged content without re-downloading it.
 */
public record MealPhotoResponse(
    Long id,
    int width,
    int height,
    long byteSize,
    String contentType,
    String checksumSha256,
    String url
) {
    public static MealPhotoResponse from(MealPhoto photo, Long mealId) {
        return new MealPhotoResponse(
            photo.getId(),
            photo.getWidth(),
            photo.getHeight(),
            photo.getByteSize(),
            photo.getContentType(),
            photo.getChecksumSha256(),
            contentPath(mealId, photo.getId())
        );
    }

    /** The authenticated content path for one photo revision. */
    public static String contentPath(Long mealId, Long photoId) {
        return "/api/v1/meals/" + mealId + "/photo?revision=" + photoId;
    }
}
