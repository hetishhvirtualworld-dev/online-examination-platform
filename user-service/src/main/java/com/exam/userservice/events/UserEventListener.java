package com.exam.userservice.events;

import java.util.Optional;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.exam.userservice.domain.entity.User;
import com.exam.userservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserEventListener {

    private final UserRepository userRepository;

    @KafkaListener(
            topics = "user-registered-topic",
            groupId = "user-service-group-prod"
    )
    public void listen(UserRegisteredEvent event) {
    	
    	System.out.println("Received message: " + event);
    	
    	Optional<User> existing = userRepository.findByExternalUserId(event.getUserId());

    	if (existing.isPresent()) {
    	    System.out.println("User already exists. Skipping...");
    	    return;
    	}

    	User profile = User.builder()
                .externalUserId(event.getUserId())
                .fullName(event.getFullName())
                .email(event.getEmail())
                .role(event.getRole())
                .build();

        userRepository.save(profile);
    }
}