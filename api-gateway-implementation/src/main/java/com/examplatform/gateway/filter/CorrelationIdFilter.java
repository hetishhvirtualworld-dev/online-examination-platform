package com.examplatform.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Correlation ID Filter
 * 
 * Generates a unique correlation ID for each request to enable distributed tracing.
 * 
 * Purpose:
 * - Track a single user request across multiple microservices
 * - Debug production issues by grepping logs with correlation ID
 * - Essential for observability in distributed systems
 * 
 * Flow:
 * 1. Check if request already has X-Correlation-Id header
 * 2. If not, generate new UUID
 * 3. Add to request headers
 * 4. Add to MDC (Mapped Diagnostic Context) for logging
 * 5. All downstream services receive and log this ID
 * 
 * Example:
 * Request: POST /api/exams/123/submit
 * Gateway generates: correlation-id=abc-123-def-456
 * Logs across all services:
 *   [Gateway] correlation-id=abc-123 | Routing to Exam Service
 *   [Exam] correlation-id=abc-123 | Processing submit
 *   [Evaluation] correlation-id=abc-123 | Scoring exam
 *   [Result] correlation-id=abc-123 | Saving result
 *   [Notification] correlation-id=abc-123 | Sending email
 * 
 * Order: -1 (Execute early, before other filters)
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_CORRELATION_ID_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Get existing correlation ID or generate new one
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = generateCorrelationId();
        }
        
        // Store in context for logging
        final String finalCorrelationId = correlationId;
        
        // Add to request headers (forwarded to backend services)
        ServerHttpRequest mutatedRequest = request.mutate()
            .header(CORRELATION_ID_HEADER, finalCorrelationId)
            .build();
        
        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(mutatedRequest)
            .build();
        
        // Log the correlation ID
        log.debug("Request {} {} with correlation ID: {}", 
            request.getMethod(), 
            request.getPath(), 
            finalCorrelationId);
        
        // Continue filter chain with correlation ID in context
        return chain.filter(mutatedExchange)
            .contextWrite(ctx -> ctx.put(MDC_CORRELATION_ID_KEY, finalCorrelationId));
    }

    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return -1; // High priority - execute early
    }
}
