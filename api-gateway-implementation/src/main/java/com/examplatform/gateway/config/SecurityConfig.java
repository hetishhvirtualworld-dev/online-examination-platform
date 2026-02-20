package com.examplatform.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Security Configuration
 * 
 * Handles JWT validation at the API Gateway level.
 * Backend services trust the gateway and don't need to re-validate JWT.
 * 
 * Flow:
 * 1. Request arrives with Authorization: Bearer <JWT>
 * 2. Gateway validates JWT signature using public key
 * 3. If valid, extracts user information (userId, roles)
 * 4. Forwards request to backend with X-User-Id header
 * 5. Backend services trust the gateway
 * 
 * Public endpoints (no JWT required):
 * - /api/auth/login
 * - /api/auth/register
 * - /api/auth/refresh
 * - /actuator/** (health checks)
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // Disable CSRF (not needed for stateless API)
            .csrf(csrf -> csrf.disable())
            
            // Disable form login (we're using JWT)
            .formLogin(formLogin -> formLogin.disable())
            
            // Disable HTTP Basic (we're using JWT)
            .httpBasic(httpBasic -> httpBasic.disable())
            
            // Don't create sessions (stateless gateway)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            
            // Authorization rules
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints (no authentication required)
                .pathMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                .pathMatchers("/actuator/**").permitAll()
                
                // All other endpoints require authentication
                .anyExchange().authenticated()
            )
            
            // JWT validation
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {
                    // JWT configuration is in application.yml
                    // spring.security.oauth2.resourceserver.jwt.jwk-set-uri
                })
            )
            
            .build();
    }
}
