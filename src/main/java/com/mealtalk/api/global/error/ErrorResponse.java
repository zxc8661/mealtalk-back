package com.mealtalk.api.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The single error body every failed request returns.
 *
 * <p>Clients read {@code message} for display, {@code code} to branch on a specific
 * failure, and {@code details} to attach messages to individual fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String message,
    String code,
    List<FieldError> details
) {
    public static ErrorResponse of(String message, ErrorCode code) {
        return new ErrorResponse(message, code.name(), null);
    }

    public static ErrorResponse of(String message, ErrorCode code, List<FieldError> details) {
        return new ErrorResponse(message, code.name(), details == null || details.isEmpty() ? null : details);
    }

    /** One rejected field, so a form can place the message next to its input. */
    public record FieldError(String field, String message) {
    }
}
