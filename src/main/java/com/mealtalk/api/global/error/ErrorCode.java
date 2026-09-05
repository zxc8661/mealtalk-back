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
    /** The upload is larger than the server accepts. */
    PAYLOAD_TOO_LARGE,
    /** The uploaded bytes are of a type the server refuses to store. */
    UNSUPPORTED_MEDIA_TYPE,
    /** Private photo storage is unreachable or not configured. The request may be retried. */
    STORAGE_UNAVAILABLE,
    /** Anything unhandled. The cause is logged, never returned. */
    INTERNAL_ERROR
}
