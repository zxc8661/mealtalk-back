package com.mealtalk.api.domain.meal.photo;

/**
 * Bytes read back out of the private bucket.
 *
 * <p>Photos are bounded to a couple of megabytes after sanitization, so the whole
 * object is held in memory rather than streamed: it removes a whole class of
 * "who closes the stream" bugs at the controller boundary.
 */
public record StoredObject(String objectKey, String contentType, long byteSize, byte[] bytes) {
}
