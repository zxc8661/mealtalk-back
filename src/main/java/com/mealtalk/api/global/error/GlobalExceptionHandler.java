package com.mealtalk.api.global.error;

import com.mealtalk.api.domain.meal.controller.MealController;
import com.mealtalk.api.domain.meal.photo.MealPhotoStorageException;
import com.mealtalk.api.domain.meal.photo.MealPhotoValidationException;
import com.mealtalk.api.domain.meal.service.MealService;
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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    /**
     * A required multipart part was omitted, such as the {@code meal} JSON part.
     *
     * <p>Without this the missing part reaches the catch-all and a plainly bad
     * request is reported as a server fault.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "필수 값이 빠졌습니다.",
            ErrorCode.VALIDATION_FAILED,
            List.of(new ErrorResponse.FieldError(exception.getRequestPartName(), "필수 항목입니다."))
        ));
    }

    /**
     * The upload exceeded the container's multipart limit.
     *
     * <p>Reported before a byte of it is buffered, which is the point: the server
     * must be able to refuse an oversized body without holding it in memory.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ErrorResponse.of("사진 용량이 너무 큽니다.", ErrorCode.PAYLOAD_TOO_LARGE));
    }

    /** A malformed multipart body: unparseable boundaries, truncated stream. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException exception) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("요청 형식이 올바르지 않습니다.", ErrorCode.MALFORMED_REQUEST));
    }

    /** A write sent as JSON rather than multipart. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedRequestType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ErrorResponse.of("지원하지 않는 요청 형식입니다.", ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    /**
     * The uploaded bytes are not an acceptable photo.
     *
     * <p>The reason decides the status, because these are genuinely different
     * failures to a client: a broken file is worth retrying with another file,
     * an unsupported format is not, and an oversized one needs resizing first.
     */
    @ExceptionHandler(MealPhotoValidationException.class)
    public ResponseEntity<ErrorResponse> handlePhotoValidation(MealPhotoValidationException exception) {
        log.debug("사진 검증 거부 {}: {}", exception.getReason(), exception.getMessage());
        return switch (exception.getReason()) {
            case EMPTY, UNREADABLE -> ResponseEntity.badRequest().body(ErrorResponse.of(
                "사진을 읽을 수 없습니다. 다른 사진을 선택해주세요.",
                ErrorCode.VALIDATION_FAILED,
                List.of(new ErrorResponse.FieldError("photo", "사진 파일이 올바르지 않습니다."))
            ));
            case UNSUPPORTED_FORMAT, ANIMATED -> ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(
                    "JPEG 또는 PNG 사진만 올릴 수 있습니다.",
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    List.of(new ErrorResponse.FieldError("photo", "지원하지 않는 사진 형식입니다."))
                ));
            case TOO_LARGE, TOO_MANY_PIXELS -> ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(
                    "사진 용량이 너무 큽니다.",
                    ErrorCode.PAYLOAD_TOO_LARGE,
                    List.of(new ErrorResponse.FieldError("photo", "사진이 허용 범위를 넘었습니다."))
                ));
        };
    }

    /**
     * Private photo storage was unreachable or is not configured yet.
     *
     * <p>503 rather than 500: the request itself was fine and retrying it later
     * can succeed. The message never mentions the bucket, endpoint or any key.
     */
    @ExceptionHandler(MealPhotoStorageException.class)
    public ResponseEntity<ErrorResponse> handlePhotoStorage(MealPhotoStorageException exception) {
        log.error("사진 저장소 오류", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.of("사진 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.", ErrorCode.STORAGE_UNAVAILABLE));
    }

    /** The record would end up with neither a memo nor a photo. */
    @ExceptionHandler(MealService.EmptyMealRecordException.class)
    public ResponseEntity<ErrorResponse> handleEmptyRecord(MealService.EmptyMealRecordException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "입력값을 다시 확인해주세요.",
            ErrorCode.VALIDATION_FAILED,
            List.of(new ErrorResponse.FieldError("memo", "사진이나 메모 중 하나는 입력해주세요."))
        ));
    }

    /** The photo action contradicts the parts that were sent with it. */
    @ExceptionHandler(MealController.InvalidPhotoActionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPhotoAction(MealController.InvalidPhotoActionException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "입력값을 다시 확인해주세요.",
            ErrorCode.VALIDATION_FAILED,
            List.of(new ErrorResponse.FieldError(exception.getField(), exception.getMessage()))
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
        } else if (resolved == HttpStatus.UNAUTHORIZED) {
            log.warn("인증 거부 응답: {}", exception.getReason());
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
            case PAYLOAD_TOO_LARGE -> "사진 용량이 너무 큽니다.";
            case UNSUPPORTED_MEDIA_TYPE -> "지원하지 않는 요청 형식입니다.";
            case SERVICE_UNAVAILABLE -> "사진 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.";
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
            case PAYLOAD_TOO_LARGE -> ErrorCode.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case SERVICE_UNAVAILABLE -> ErrorCode.STORAGE_UNAVAILABLE;
            default -> status.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }
}
