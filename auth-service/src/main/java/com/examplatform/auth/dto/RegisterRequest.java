package com.examplatform.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * RegisterRequest — new user registration.
 * POST /api/auth/register
 */
@Data
public class RegisterRequest {

    @NotBlank @Email
    public String email;

    @NotBlank @Size(min = 8, max = 128)
    public String password;

    @NotBlank
    public String firstName;

    @NotBlank
    public String lastName;

    @NotBlank
    @Pattern(regexp = "STUDENT|EXAMINER|ADMIN", message = "Role must be STUDENT, EXAMINER, or ADMIN")
    public String role;
}
