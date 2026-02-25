package com.examplatform.auth.client;

import com.examplatform.auth.dto.UserDto;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

/**
 * UserServiceClient — calls User Service to validate credentials.
 *
 * Auth Service does NOT store user profiles or passwords.
 * User Service owns that data. We call it during login to:
 *   1. Fetch user by email (does this user exist?)
 *   2. Get the BCrypt password hash (to verify against what client sent)
 *   3. Check account status (active? locked?)
 *
 * On token refresh, called again to re-validate user is still active
 * (handles cases where admin locked the account since last login).
 */
@Component
@Slf4j
public class UserServiceClientWebClient {

    private final WebClient webClient;

    public UserServiceClientWebClient(@Value("${services.user-service.url}") String userServiceUrl) {
        this.webClient = WebClient.builder()
        
        		
        		.baseUrl(userServiceUrl)
                .defaultHeader("X-Internal-Service", "auth-service")
                .build();
    }

    /**
     * Register a new user — delegates profile creation to User Service.
     * Auth Service never stores user profiles.
     */
    public UserDto register(String email, String firstName, String lastName, String role) {
        try {
            return webClient.post()
                    .uri("/api/users")
                    .bodyValue(new RegisterRequest(email, firstName, lastName, role))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.createError())
                    .bodyToMono(UserDto.class)
                    .block();

        } catch (Exception e) {
            log.error("User Service call failed for register: {}", e.getMessage());
            throw new RuntimeException("User Service unavailable");
        }
    }

    // Internal record for the register request body
    private record RegisterRequest(
            String email,
            String firstName, String lastName, String role) {}
}
