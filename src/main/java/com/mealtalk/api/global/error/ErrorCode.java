package com.mealtalk.api.global.error;

/** Stable identifiers clients can branch on without parsing prose. */
public enum ErrorCode {
    /** Request body or parameters failed validation. */
    VALIDATION_FAILED,
    /** The request could not be parsed or a parameter had the wrong type. */
    MALFORMED_REQUEST,
    /** Authentication is missing or no longer valid. */
    UNAUTHORIZED,
    /** The caller may not access this resource. */
    FORBIDDEN,
    /** The requested resource does not exist or is not visible to the caller. */
    NOT_FOUND,
    /** The request conflicts with current server state. */
    CONFLICT,
    /** Anything unhandled. The cause is logged, never returned. */
    INTERNAL_ERROR
}
