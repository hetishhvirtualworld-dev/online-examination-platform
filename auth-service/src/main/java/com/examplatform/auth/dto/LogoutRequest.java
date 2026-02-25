package com.examplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * LogoutRequest — OAuth2.0 Token Revocation (RFC 7009).
 * POST /api/auth/logout
 */
@Data
public class LogoutRequest {

    @NotBlank(message = "Refresh token is required")
    public String refreshToken;

    // true = revoke all sessions for this user, false = current device only
    public boolean allDevices;
}
