package com.examplatform.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate Limiting Configuration
 * 
 * Prevents abuse by limiting the number of requests per user.
 * Uses Redis for shared state across multiple gateway instances.
 * 
 * Why Redis?
 * - Shared state: Multiple gateway instances see the same counters
 * - Atomic operations: INCR is atomic, no race conditions
 * - Performance: Sub-millisecond latency (<1ms vs 5-10ms for database)
 * - TTL support: Redis auto-deletes expired counters
 * 
 * Problem without Redis:
 * - Gateway Instance 1 tracks 100 requests from user
 * - Gateway Instance 2 tracks 100 requests from same user
 * - User bypassed 100 req/min limit by hitting both instances
 * 
 * Algorithm (Redis Sliding Window):
 * key = "rate-limit:" + userId + ":" + currentMinute
 * count = INCR key
 * if count == 1:
 *     EXPIRE key 60  // Auto-delete after 60 seconds
 * if count > limit:
 *     return 429 Too Many Requests
 * 
 * Rate limits configured in application.yml per route:
 * - User Service: 10 req/sec burst 20
 * - Exam Service: 20 req/sec burst 40
 * 
 * Key Resolution Strategy:
 * - Use JWT sub (userId) for authenticated requests
 * - Use IP address for public endpoints
 * - Can be customized per business logic
 */
@Configuration
public class RateLimitConfig {

    /**
     * Key Resolver for Rate Limiting
     * 
     * Determines the key used for rate limiting.
     * Authenticated requests: Use userId from JWT
     * Public requests: Use IP address
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Try to get userId from X-User-Id header (set by JwtUserInfoFilter)
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            
            // Fallback to IP address for public endpoints
            String ipAddress = exchange.getRequest().getRemoteAddress() != null 
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
            
            return Mono.just(ipAddress);
        };
    }
}
