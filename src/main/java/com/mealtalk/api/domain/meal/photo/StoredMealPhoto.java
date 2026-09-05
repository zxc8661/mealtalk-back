package com.mealtalk.api.domain.meal.photo;

/**
 * A sanitized photo that has been written to the private bucket.
 *
 * <p>Field-for-field what the {@code meal_photos} row needs. The
 * {@code objectKey} is server-internal and must never appear in an API response.
 */
public record StoredMealPhoto(
    String objectKey,
    String contentType,
    long byteSize,
    int width,
    int height,
    String checksumSha256
) {
}
