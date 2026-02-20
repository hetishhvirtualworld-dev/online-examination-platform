package com.exam.userservice.service.impl;

import com.exam.userservice.domain.entity.User;
import com.exam.userservice.dto.request.CreateUserRequest;
import com.exam.userservice.dto.response.UserResponse;
import com.exam.userservice.exception.UserNotFoundException;
import com.exam.userservice.mapper.UserMapper;
import com.exam.userservice.repository.UserRepository;
import com.exam.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

	@Autowired
    private final UserRepository userRepository;

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.getEmail());

        User user = UserMapper.toEntity(request);
        User savedUser = userRepository.save(user);

        return UserMapper.toResponse(savedUser);
    }

    @Override
    public UserResponse getUserById(Long id) {
        log.info("Fetching user with id: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return UserMapper.toResponse(user);
    }
}
