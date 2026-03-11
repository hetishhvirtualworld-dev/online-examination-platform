package com.examplatform.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * JWT User Info Filter
 * 
 * Extracts user information from validated JWT and forwards to backend services.
 * 
 * Flow:
 * 1. JWT already validated by Spring Security (SecurityConfig)
 * 2. Extract claims: userId, email, roles
 * 3. Add custom headers for backend services:
 *    - X-User-Id: Unique user identifier
 *    - X-User-Email: User's email
 *    - X-User-Roles: Comma-separated roles
 * 4. Backend services trust the gateway and use these headers
 * 
 * Why forward user info via headers?
 * - Backend services don't need JWT validation logic
 * - Backend services don't need public keys
 * - Simpler backend implementation
 * - Performance: Validate once at gateway, not 6 times across services
 * 
 * Security consideration:
 * - These headers are added AFTER JWT validation
 * - Backend services should ONLY accept requests from the gateway
 * - Use network policies or service mesh to enforce this
 * 
 * Example:
 * JWT claims: { "sub": "user-123", "email": "student@exam.com", "roles": ["STUDENT"] }
 * Headers forwarded:
 *   X-User-Id: user-123
 *   X-User-Email: student@exam.com
 *   X-User-Roles: STUDENT
 * 
 * Order: 0 (Execute after JWT validation, before routing)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUserInfoFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    	
    	return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> {

                    Jwt jwt = auth.getToken();

        			String userId = jwt.getSubject();
        			String email = jwt.getClaim("email");
        			String roles = jwt.getClaim("roles");
        			
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().header(HEADER_USER_ID, userId)
        					.header(HEADER_USER_EMAIL, email).header(HEADER_USER_ROLES, roles).build();

                    return chain.filter(
                            exchange.mutate()
                                    .request(mutatedRequest)
                                    .build()
                    );
                })
                .switchIfEmpty(chain.filter(exchange));
	}

    @Override
    public int getOrder() {
        return 0; // Medium priority - after JWT validation, before routing
    }
}
