package com.mealtalk.api.domain.meal.photo;

/**
 * The uploaded bytes are not an acceptable photo.
 *
 * <p>Always the caller's fault, so this maps to a 4xx. {@link Reason} lets the
 * API layer choose between 400, 413 and 415 without parsing the message.
 */
public class MealPhotoValidationException extends RuntimeException {
    public enum Reason {
        /** No bytes were supplied. */
        EMPTY,
        /** The upload exceeded the accepted byte size. */
        TOO_LARGE,
        /** No image decoder recognised the bytes, or decoding failed. */
        UNREADABLE,
        /** The bytes decoded, but not as JPEG or PNG. */
        UNSUPPORTED_FORMAT,
        /** The image carries more than one frame. */
        ANIMATED,
        /** The declared raster exceeds the accepted pixel count. */
        TOO_MANY_PIXELS
    }

    private final Reason reason;

    public MealPhotoValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
