package com.examplatform.auth.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * LoginRequest — OAuth2.0 Resource Owner Password Credentials grant.
 * POST /api/auth/login
 */
@Data
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email")
    public String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128)
    public String password;

    public String deviceInfo;
}
