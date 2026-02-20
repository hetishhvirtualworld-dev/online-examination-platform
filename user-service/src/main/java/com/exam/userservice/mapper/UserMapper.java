package com.exam.userservice.mapper;

import com.exam.userservice.domain.entity.User;
import com.exam.userservice.dto.request.CreateUserRequest;
import com.exam.userservice.dto.response.UserResponse;

public class UserMapper {

    public static User toEntity(CreateUserRequest request) {
        return User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .role(request.getRole())
                .status("ACTIVE")
                .build();
    }

    public static UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }
}
