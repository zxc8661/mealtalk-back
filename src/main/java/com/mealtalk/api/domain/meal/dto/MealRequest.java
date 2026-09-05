package com.mealtalk.api.domain.meal.dto;

import com.mealtalk.api.domain.meal.entity.MealType;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;

/**
 * The {@code meal} JSON part of a multipart meal write. The record itself is a
 * date, a type, an optional eaten time and a memo; the photo travels as the
 * separate binary {@code photo} part.
 *
 * <p>{@code memo} is normalized before it is measured or stored, so surrounding
 * whitespace never consumes the 1,000 character budget and a whitespace-only
 * memo counts as no memo at all.
 *
 * <p>The record invariant - {@code memo != null || photo != null} - deliberately
 * lives in the service, not here: this part cannot see the photo part, and on an
 * update the answer depends on the {@link MealPhotoAction} applied to the photo
 * the record already has. Bean validation would only ever see half the state.
 */
public record MealRequest(
    @NotNull LocalDate mealDate,
    @NotNull MealType mealType,
    Instant eatenAt,
    @MemoLength String memo,
    /** Required on update, ignored on create (a new record has no photo to keep). */
    MealPhotoAction photoAction,
    /** Optional UUID making a retried create idempotent. */
    @ClientRequestId String clientRequestId
) {
    public static final int MEMO_MAX_LENGTH = 1000;

    /** The stored form of {@code memo}: outer whitespace removed, blank becomes {@code null}. */
    public String normalizedMemo() {
        return normalizeMemo(memo);
    }

    /** The parsed request id, or {@code null} when the client did not send one. */
    public UUID parsedClientRequestId() {
        String raw = clientRequestId == null ? null : clientRequestId.trim();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return UUID.fromString(raw);
    }

    public static String normalizeMemo(String rawMemo) {
        if (rawMemo == null) {
            return null;
        }
        String trimmed = rawMemo.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Documented
    @Constraint(validatedBy = MemoLengthValidator.class)
    @Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MemoLength {
        String message() default "메모는 1000자까지 입력할 수 있습니다.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class MemoLengthValidator implements ConstraintValidator<MemoLength, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            String normalized = normalizeMemo(value);
            return normalized == null || normalized.length() <= MEMO_MAX_LENGTH;
        }
    }

    @Documented
    @Constraint(validatedBy = ClientRequestIdValidator.class)
    @Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ClientRequestId {
        String message() default "요청 식별자는 UUID 형식이어야 합니다.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class ClientRequestIdValidator implements ConstraintValidator<ClientRequestId, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.trim().isEmpty()) {
                return true; // the id is optional; only a supplied one must be well formed
            }
            try {
                UUID.fromString(value.trim());
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }
}
