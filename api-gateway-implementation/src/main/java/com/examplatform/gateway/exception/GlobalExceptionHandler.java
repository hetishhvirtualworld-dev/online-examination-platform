package com.examplatform.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler
 * 
 * Handles all exceptions thrown by the gateway and returns consistent error responses.
 * 
 * Common Gateway Errors:
 * - 401 Unauthorized: JWT validation failed
 * - 403 Forbidden: Authenticated but not authorized
 * - 429 Too Many Requests: Rate limit exceeded
 * - 502 Bad Gateway: Backend service unavailable
 * - 504 Gateway Timeout: Backend service timeout
 * 
 * Error Response Format:
 * {
 *   "timestamp": "2024-02-15T10:30:00",
 *   "status": 429,
 *   "error": "Too Many Requests",
 *   "message": "Rate limit exceeded. Try again later.",
 *   "path": "/api/exams/123",
 *   "correlationId": "abc-123-def-456"
 * }
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = determineHttpStatus(ex);
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        
        log.error("Gateway error: status={}, path={}, correlationId={}, error={}", 
            status.value(),
            exchange.getRequest().getPath(),
            correlationId,
            ex.getMessage(),
            ex);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", getUserFriendlyMessage(ex, status));
        errorResponse.put("path", exchange.getRequest().getPath().value());
        errorResponse.put("correlationId", correlationId);
        
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error serializing error response", e);
            return exchange.getResponse().setComplete();
        }
    }

    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            return (HttpStatus) ((ResponseStatusException) ex).getStatusCode();
        }
        
        // JWT validation errors
        if (ex.getMessage() != null && ex.getMessage().contains("JWT")) {
            return HttpStatus.UNAUTHORIZED;
        }
        
        // Rate limiting errors
        if (ex.getMessage() != null && ex.getMessage().contains("rate limit")) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        
        // Connection errors
        if (ex instanceof java.net.ConnectException) {
            return HttpStatus.BAD_GATEWAY;
        }
        
        // Timeout errors
        if (ex instanceof java.util.concurrent.TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        
        // Default to 500
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String getUserFriendlyMessage(Throwable ex, HttpStatus status) {
        switch (status) {
            case UNAUTHORIZED:
                return "Authentication required. Please login.";
            case FORBIDDEN:
                return "You don't have permission to access this resource.";
            case TOO_MANY_REQUESTS:
                return "Rate limit exceeded. Please try again later.";
            case BAD_GATEWAY:
                return "Service temporarily unavailable. Please try again.";
            case GATEWAY_TIMEOUT:
                return "Request timeout. Please try again.";
            default:
                return "An error occurred processing your request.";
        }
    }
}
