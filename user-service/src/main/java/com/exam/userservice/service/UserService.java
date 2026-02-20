package com.exam.userservice.service;

import com.exam.userservice.dto.request.CreateUserRequest;
import com.exam.userservice.dto.response.UserResponse;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse getUserById(Long id);
}
