package com.examplatform.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.time.Instant;

/**
 * All Auth Service DTOs in one file for readability.
 * Uses Java records for immutability.
 * @JsonInclude(NON_NULL) prevents null fields from cluttering JSON responses.
 */
public final class AuthDtos {

    private AuthDtos() {}

    // ==============================================================
    //  REQUEST DTOs
    // ==============================================================

    /**
     * OAuth2.0 Resource Owner Password Credentials (ROPC) grant.
     * POST /api/auth/login
     */
    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
            String password,

            // Optional: for multi-device session tracking
            String deviceInfo
    ) {}

    /**
     * OAuth2.0 Refresh Token grant.
     * POST /api/auth/refresh
     */
    public record RefreshTokenRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {}

    /**
     * Token revocation (RFC 7009).
     * POST /api/auth/logout
     */
    public record LogoutRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken,

            // true = revoke all devices, false = current device only
            boolean allDevices
    ) {}

    /**
     * Registration — delegates profile creation to User Service.
     * POST /api/auth/register
     */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255)
            String email,

            @NotBlank @Size(min = 8, max = 128)
            String password,

            @NotBlank @Size(min = 2, max = 100)
            String firstName,

            @NotBlank @Size(min = 2, max = 100)
            String lastName,

            @NotNull
            UserRole role
    ) {}

    // ==============================================================
    //  RESPONSE DTOs
    // ==============================================================

    /**
     * OAuth2.0 token response — RFC 6749 standard fields.
     */
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,   // Always "Bearer"
            long expiresIn,     // Seconds until access token expiry
            String scope
    ) {
        public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
            return TokenResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(expiresIn)
                    .scope("read write")
                    .build();
        }
    }

    /**
     * Consistent error response across all failure modes.
     */
    @Builder
    public record ErrorResponse(
            int status,
            String error,
            String message,
            String path,
            Instant timestamp
    ) {}

    /**
     * User DTO received from User Service internal API.
     */
    public record UserDto(
            String id,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            UserRole role,
            boolean active,
            boolean locked
    ) {}

    /**
     * Active session info for "manage devices" UI.
     */
    @Builder
    public record ActiveSessionResponse(
            String sessionId,       // First 8 chars of refresh token (masked)
            String deviceInfo,
            Instant createdAt,
            Instant expiresAt
    ) {}

    // ==============================================================
    //  ENUMS
    // ==============================================================

    public enum UserRole {
        STUDENT, ADMIN, EXAMINER
    }
}
