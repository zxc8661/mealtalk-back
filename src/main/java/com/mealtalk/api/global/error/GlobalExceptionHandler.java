package com.mealtalk.api.global.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Renders every failure as {@link ErrorResponse} so clients always find
 * {@code message}, {@code code} and optional {@code details}.
 *
 * <p>Without this advice Spring returns its default body, which carries no
 * {@code message} field at all and leaves clients with nothing to show.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean validation on a request body. Reports the offending fields. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException exception) {
        List<ErrorResponse.FieldError> details = exception.getBindingResult().getAllErrors().stream()
            .map(error -> new ErrorResponse.FieldError(
                error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                error.getDefaultMessage() == null ? "올바르지 않은 값입니다." : error.getDefaultMessage()
            ))
            .toList();
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("입력값을 다시 확인해주세요.", ErrorCode.VALIDATION_FAILED, details));
    }

    /** Bean validation on parameters rather than a body. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<ErrorResponse.FieldError> details = exception.getConstraintViolations().stream()
            .map(violation -> new ErrorResponse.FieldError(lastNode(violation), violation.getMessage()))
            .toList();
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("입력값을 다시 확인해주세요.", ErrorCode.VALIDATION_FAILED, details));
    }

    /** Unparseable JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("요청 형식이 올바르지 않습니다.", ErrorCode.MALFORMED_REQUEST));
    }

    /** A query parameter of the wrong type, such as date=not-a-date. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "요청 형식이 올바르지 않습니다.",
            ErrorCode.MALFORMED_REQUEST,
            List.of(new ErrorResponse.FieldError(exception.getName(), "값의 형식이 올바르지 않습니다."))
        ));
    }

    /** A required query parameter was omitted. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "필수 값이 빠졌습니다.",
            ErrorCode.VALIDATION_FAILED,
            List.of(new ErrorResponse.FieldError(exception.getParameterName(), "필수 값입니다."))
        ));
    }

    /**
     * Services signal expected failures with this, carrying their own status.
     *
     * <p>Their {@code reason} is English developer text such as "Meal not found",
     * so it is logged rather than shown; the client receives the Korean message
     * for that status.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        if (resolved.is5xxServerError()) {
            log.error("서버 오류 응답: {}", exception.getReason(), exception);
        } else {
            log.debug("클라이언트 오류 응답 {}: {}", resolved.value(), exception.getReason());
        }
        return ResponseEntity.status(resolved)
            .body(ErrorResponse.of(defaultMessage(resolved), codeFor(resolved)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of("로그인이 필요합니다.", ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of("접근 권한이 없습니다.", ErrorCode.FORBIDDEN));
    }

    /**
     * An unmapped path. {@link NoResourceFoundException} must be handled explicitly:
     * it otherwise reaches the catch-all below and turns a plain 404 into a 500.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoHandler(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("요청한 경로를 찾을 수 없습니다.", ErrorCode.NOT_FOUND));
    }

    /** Last resort. The cause is logged; the response never leaks internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("처리하지 못한 예외", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("서버에서 문제가 발생했습니다.", ErrorCode.INTERNAL_ERROR));
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int separator = path.lastIndexOf('.');
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    private static String defaultMessage(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "로그인이 필요합니다.";
            case FORBIDDEN -> "접근 권한이 없습니다.";
            case NOT_FOUND -> "요청한 정보를 찾을 수 없습니다.";
            case CONFLICT -> "이미 처리된 요청입니다.";
            case BAD_REQUEST -> "입력값을 다시 확인해주세요.";
            default -> "서버에서 문제가 발생했습니다.";
        };
    }

    private static ErrorCode codeFor(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case CONFLICT -> ErrorCode.CONFLICT;
            case BAD_REQUEST -> ErrorCode.VALIDATION_FAILED;
            default -> status.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }
}
