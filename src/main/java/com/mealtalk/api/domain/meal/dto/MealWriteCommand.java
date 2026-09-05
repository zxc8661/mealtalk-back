package com.mealtalk.api.domain.meal.dto;

/**
 * One multipart meal write, with the two parts already unpacked.
 *
 * <p>The controller owns HTTP: it parses the parts and rejects action/part
 * combinations that cannot mean anything. The service owns the record, so it
 * receives a shape where those checks have already passed and the only remaining
 * question is what the resulting record looks like.
 *
 * @param request      the {@code meal} JSON part
 * @param photoBytes   the raw bytes of the {@code photo} part, or {@code null} when absent
 * @param action       what to do with the record's photo; {@link MealPhotoAction#REPLACE}
 *                     whenever {@code photoBytes} is present
 */
public record MealWriteCommand(
    MealRequest request,
    byte[] photoBytes,
    MealPhotoAction action
) {
    public boolean hasPhotoUpload() {
        return photoBytes != null && action == MealPhotoAction.REPLACE;
    }
}
