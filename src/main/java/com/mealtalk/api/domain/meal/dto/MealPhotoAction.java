package com.mealtalk.api.domain.meal.dto;

/**
 * What an update should do with the record's existing photo.
 *
 * <p>The action is required on update and never inferred from the presence of a
 * file part: "no file" is ambiguous between "leave it alone" and "delete it",
 * and guessing wrong destroys a user's photo. Each value therefore pins exactly
 * one legal shape of the request, and every other combination is a 400.
 */
public enum MealPhotoAction {
    /** Leave the current photo exactly as it is. No {@code photo} part may be sent. */
    KEEP,
    /** Delete the current photo. No {@code photo} part may be sent. */
    REMOVE,
    /** Store the supplied photo in place of the current one. A {@code photo} part is required. */
    REPLACE
}
