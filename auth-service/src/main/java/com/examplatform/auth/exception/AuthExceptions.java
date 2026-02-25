package com.examplatform.auth.exception;

import lombok.Getter;

/**
 * All Auth Service exceptions.
 *
 * Design: specific exception per failure mode, all RuntimeException.
 * GlobalExceptionHandler maps each to the correct HTTP status.
 */
public final class AuthExceptions {

    private AuthExceptions() {}

    // ------------------------------------------------------------------
    // TokenException — JWT validation failures
    // ------------------------------------------------------------------

    @Getter
    public static class TokenException extends RuntimeException {
        private final TokenErrorCode errorCode;

        public TokenException(TokenErrorCode errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public enum TokenErrorCode {
            EXPIRED, INVALID_SIGNATURE, MALFORMED, UNSUPPORTED, MISSING, REVOKED
        }
    }

    // ------------------------------------------------------------------
    // Authentication failures — always same message (no user enumeration)
    // ------------------------------------------------------------------

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }

    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) {
            super(message);
        }
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException() {
            super("Invalid or expired refresh token");
        }
    }

    // ------------------------------------------------------------------
    // Downstream service errors
    // ------------------------------------------------------------------

    public static class UserServiceException extends RuntimeException {
        public UserServiceException(String message) { super(message); }
        public UserServiceException(String message, Throwable cause) { super(message, cause); }
    }

    // ------------------------------------------------------------------
    // Policy violations
    // ------------------------------------------------------------------

    public static class MaxSessionsExceededException extends RuntimeException {
        public MaxSessionsExceededException(int max) {
            super("Maximum concurrent sessions (" + max + ") exceeded. Please log out from another device.");
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) { super(message); }
    }
}
