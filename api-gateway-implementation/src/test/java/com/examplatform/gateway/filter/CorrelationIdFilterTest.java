package com.examplatform.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CorrelationIdFilter
 * 
 * Tests:
 * 1. Generate new correlation ID if not present
 * 2. Preserve existing correlation ID
 * 3. Add correlation ID to request headers
 * 4. Verify filter order
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        chain = mock(GatewayFilterChain.class);
    }

    @Test
    void shouldGenerateCorrelationIdWhenNotPresent() {
        // Given: Request without correlation ID
        MockServerHttpRequest request = MockServerHttpRequest
            .get("http://localhost/api/users")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Mock chain to return empty Mono
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, chain);

        // Then: Correlation ID should be generated and added
        StepVerifier.create(result)
            .verifyComplete();

        // Verify correlation ID was added to request
        String correlationId = exchange.getRequest()
            .getHeaders()
            .getFirst("X-Correlation-Id");
        
        assertThat(correlationId)
            .isNotNull()
            .isNotBlank();
    }

    @Test
    void shouldPreserveExistingCorrelationId() {
        // Given: Request with existing correlation ID
        String existingCorrelationId = "test-correlation-123";
        MockServerHttpRequest request = MockServerHttpRequest
            .get("http://localhost/api/users")
            .header("X-Correlation-Id", existingCorrelationId)
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, chain);

        // Then: Existing correlation ID should be preserved
        StepVerifier.create(result)
            .verifyComplete();

        String correlationId = exchange.getRequest()
            .getHeaders()
            .getFirst("X-Correlation-Id");
        
        assertThat(correlationId).isEqualTo(existingCorrelationId);
    }

    @Test
    void shouldHaveHighPriorityOrder() {
        // Then: Filter should execute early in the chain
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    @Test
    void shouldAddCorrelationIdToRequestHeaders() {
        // Given: Request without correlation ID
        MockServerHttpRequest request = MockServerHttpRequest
            .get("http://localhost/api/exams/123")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When: Filter is applied
        filter.filter(exchange, chain).block();

        // Then: Request should have correlation ID header
        HttpHeaders headers = exchange.getRequest().getHeaders();
        assertThat(headers.containsKey("X-Correlation-Id")).isTrue();
    }
}
