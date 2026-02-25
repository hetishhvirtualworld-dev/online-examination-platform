package com.examplatform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * TokenResponse — standard OAuth2.0 token response (RFC 6749).
 * Returned from /login, /refresh, and /register.
 */
@Data
@Builder
@AllArgsConstructor
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;    // Always "Bearer"
    private long   expiresIn;   // Seconds until access token expiry
    private String scope;

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
