package com.mealtalk.api.domain.meal.photo;

/**
 * The result of sanitization: safe bytes plus everything worth persisting.
 *
 * <p>Deliberately a plain record with no JPA or storage dependency, so the
 * sanitizer stays usable from a unit test, from the storage layer, and from the
 * service that writes the {@code meal_photos} row.
 *
 * @param jpegBytes      re-encoded JPEG bytes; never the original upload
 * @param contentType    always {@code image/jpeg}
 * @param byteSize       length of {@code jpegBytes}
 * @param width          width of the stored image in pixels
 * @param height         height of the stored image in pixels
 * @param checksumSha256 lowercase hex SHA-256 of {@code jpegBytes}, 64 characters
 */
public record SanitizedMealPhoto(
    byte[] jpegBytes,
    String contentType,
    long byteSize,
    int width,
    int height,
    String checksumSha256
) {
}
