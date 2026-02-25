package com.examplatform.auth.exception;

import com.examplatform.auth.dto.AuthDtos.ErrorResponse;
import com.examplatform.auth.exception.AuthExceptions.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralized exception handling.
 *
 * Principles:
 * - Consistent JSON error format for ALL error types
 * - Never expose internal details (stack traces, SQL errors) to clients
 * - Same error message for all credential failures (user enumeration prevention)
 * - Log level appropriate to the error severity
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // === 401 Unauthorized ===

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handle(InvalidCredentialsException ex, HttpServletRequest req) {
        log.info("Authentication failure: path={}", req.getRequestURI());
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handle(InvalidRefreshTokenException ex, HttpServletRequest req) {
        log.info("Invalid refresh token attempt");
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<ErrorResponse> handle(TokenException ex, HttpServletRequest req) {
        log.debug("Token validation failed: {} - {}", ex.getErrorCode(), ex.getMessage());
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    // === 403 Forbidden ===

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handle(AccountLockedException ex, HttpServletRequest req) {
        log.warn("Login attempt on locked/inactive account");
        return respond(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handle(AccessDeniedException ex, HttpServletRequest req) {
        return respond(HttpStatus.FORBIDDEN, "Access denied", req);
    }

    @ExceptionHandler(MaxSessionsExceededException.class)
    public ResponseEntity<ErrorResponse> handle(MaxSessionsExceededException ex, HttpServletRequest req) {
        return respond(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    // === 400 Bad Request ===

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return respond(HttpStatus.BAD_REQUEST, message, req);
    }

    // === 429 Too Many Requests ===

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handle(RateLimitExceededException ex, HttpServletRequest req) {
        log.warn("Rate limit exceeded: path={}", req.getRequestURI());
        return respond(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req);
    }

    // === 503 Service Unavailable ===

    @ExceptionHandler(UserServiceException.class)
    public ResponseEntity<ErrorResponse> handle(UserServiceException ex, HttpServletRequest req) {
        log.warn("User Service call failed: {}", ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication service temporarily unavailable. Please try again.", req);
    }

    // === 500 Internal Server Error ===

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handle(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error in Auth Service: {}", ex.getMessage(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, HttpServletRequest req) {
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(req.getRequestURI())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
