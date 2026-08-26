package com.mealtalk.api.domain.auth.google;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidGoogleTokenException extends ResponseStatusException {
    public InvalidGoogleTokenException(String reason) {
        super(HttpStatus.UNAUTHORIZED, reason);
    }

    public InvalidGoogleTokenException(String reason, Throwable cause) {
        super(HttpStatus.UNAUTHORIZED, reason, cause);
    }
}
