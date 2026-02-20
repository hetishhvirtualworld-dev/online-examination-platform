package com.exam.userservice.dto.request;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String fullName;
    private String email;
    private String role;
}
