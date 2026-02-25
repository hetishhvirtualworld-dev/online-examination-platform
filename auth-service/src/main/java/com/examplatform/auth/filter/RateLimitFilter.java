package com.examplatform.auth.filter;

import com.examplatform.auth.exception.AuthExceptions.RateLimitExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Servlet filter that catches RateLimitExceededException from the service layer
 * and converts it into a proper HTTP 429 response.
 *
 * WHY A FILTER AND NOT JUST GlobalExceptionHandler?
 * GlobalExceptionHandler only catches exceptions from @RestController methods.
 * Exceptions thrown during the filter chain (before reaching the controller)
 * are not caught there. This filter wraps the chain to catch rate limit
 * exceptions and return a clean JSON 429 response.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RateLimitExceededException e) {
            writeJsonResponse(response, request, HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        }
    }

    private void writeJsonResponse(HttpServletResponse response, HttpServletRequest request,
                                    HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "status",    status.value(),
                "error",     status.getReasonPhrase(),
                "message",   message,
                "path",      request.getRequestURI(),
                "timestamp", Instant.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
