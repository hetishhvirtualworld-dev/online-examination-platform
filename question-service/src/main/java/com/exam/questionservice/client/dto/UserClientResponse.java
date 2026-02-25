package com.exam.questionservice.client.dto;

import lombok.Data;

@Data
public class UserClientResponse {
    private Long id;
    private String fullName;
    private String email;
    private String role;
    private String status;
}