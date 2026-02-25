package com.examplatform.auth.dto;

import lombok.Data;

/**
 * UserDto — user data received from User Service.
 * Used during login (credential validation) and refresh (re-validation).
 */
@Data
public class UserDto {
    public String id;
    public String email;
    public String passwordHash;   // BCrypt hash — compared against login password
    public String firstName;
    public String lastName;
    public String role;           // STUDENT, EXAMINER, ADMIN
    public boolean active;
    public boolean locked;
}
