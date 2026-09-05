package com.mealtalk.api.domain.meal.photo;

/**
 * The private bucket could not serve the request.
 *
 * <p>This means infrastructure, never user input: an unreachable endpoint, a
 * refused credential, or storage that has not been configured yet. Callers map
 * it to HTTP 503; the message is developer text and must not be shown to a user.
 */
public class MealPhotoStorageException extends RuntimeException {
    public MealPhotoStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
