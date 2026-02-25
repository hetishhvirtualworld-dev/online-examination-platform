package com.examplatform.auth.client;

import com.examplatform.auth.dto.AuthDtos.UserDto;
import com.examplatform.auth.exception.AuthExceptions.UserServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * HTTP client for internal Auth Service -> User Service calls.
 *
 * Used for:
 * - Login: fetch user by email to validate credentials
 * - Refresh: re-validate user is still active (may have been locked since token issued)
 * - Register: delegate profile creation to User Service
 *
 * Security:
 * - Calls /internal/** endpoints NOT exposed via API Gateway
 * - Protected by Kubernetes NetworkPolicy (only Auth Service pod can call User Service's internal API)
 * - Carries X-Internal-Service header (set in WebClientConfig)
 *
 * Resilience:
 * - @CircuitBreaker: opens after 50% failures in 10-request window, stays open 30s
 * - @Retry: retries up to 2 times on transient network errors
 * - Fallbacks throw UserServiceException -> mapped to 503 in GlobalExceptionHandler
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final WebClient userServiceWebClient;

    /**
     * Fetch user by email — called during login.
     *
     * @param email user's email (the credential being validated)
     * @return Optional.empty() if user not found (404)
     * @throws UserServiceException if User Service is unavailable
     */
    @CircuitBreaker(name = "userService", fallbackMethod = "findByEmailFallback")
    @Retry(name = "userService")
    public Optional<UserDto> findByEmail(String email) {
        log.debug("[UserServiceClient] findByEmail called");

        UserDto user = userServiceWebClient
                .get()
                .uri("/api/users/email/{email}", email)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.empty();  // User not found — return empty Optional
                    }
                    return response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(
                                    new UserServiceException("User Service 4xx: " + response.statusCode())
                            ));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new UserServiceException("User Service 5xx: " + response.statusCode()))
                )
                .bodyToMono(UserDto.class)
                .block();

        return Optional.ofNullable(user);
    }

    /**
     * Fetch user by ID — called during token refresh to re-validate user status.
     * If user has been locked or deactivated since token was issued, deny the refresh.
     */
    @CircuitBreaker(name = "userService", fallbackMethod = "findByIdFallback")
    @Retry(name = "userService")
    public Optional<UserDto> findById(String userId) {
        log.debug("[UserServiceClient] findById called");

        UserDto user = userServiceWebClient
                .get()
                .uri("/api/users/{id}", userId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.statusCode().value() == 404
                                ? Mono.empty()
                                : Mono.error(new UserServiceException("User Service 4xx: " + response.statusCode()))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new UserServiceException("User Service 5xx"))
                )
                .bodyToMono(UserDto.class)
                .block();

        return Optional.ofNullable(user);
    }

    /**
     * Register a new user — delegates to User Service.
     * Auth Service does NOT store user profiles. It only manages tokens.
     */
    @CircuitBreaker(name = "userService", fallbackMethod = "registerFallback")
    public UserDto registerUser(Object registerRequest) {
        return userServiceWebClient
                .post()
                .uri("/api/users/register")
                .bodyValue(registerRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new UserServiceException("Registration failed: " + response.statusCode())
                                ))
                )
                .bodyToMono(UserDto.class)
                .block();
    }

    // ==============================================================
    //  CIRCUIT BREAKER FALLBACKS
    // ==============================================================

    public Optional<UserDto> findByEmailFallback(String email, Throwable t) {
        log.warn("[UserServiceClient] Circuit OPEN for findByEmail: {}", t.getMessage());
        throw new UserServiceException("User Service unavailable", t);
    }

    public Optional<UserDto> findByIdFallback(String userId, Throwable t) {
        log.warn("[UserServiceClient] Circuit OPEN for findById: {}", t.getMessage());
        throw new UserServiceException("User Service unavailable", t);
    }

    public UserDto registerFallback(Object request, Throwable t) {
        log.warn("[UserServiceClient] Circuit OPEN for registerUser: {}", t.getMessage());
        throw new UserServiceException("Registration service unavailable", t);
    }
}
