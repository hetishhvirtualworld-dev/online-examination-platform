package com.examplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RefreshRequest — OAuth2.0 Refresh Token grant.
 * POST /api/auth/refresh
 */
@Data
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    public String refreshToken;
}
