package com.examplatform.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logging Filter
 * 
 * Logs incoming requests and outgoing responses with performance metrics.
 * 
 * Why log at the gateway?
 * - Gateway sees the COMPLETE request lifecycle (client → gateway → backend → gateway → client)
 * - Backend services only see their own processing time
 * - Gateway captures:
 *   * Network latency
 *   * JWT validation time
 *   * Routing time
 *   * Backend processing time
 *   * Total end-to-end latency
 * 
 * Metrics exposed:
 * - Request path, method, status
 * - Response time in milliseconds
 * - Correlation ID for tracing
 * 
 * These logs feed into:
 * - ELK Stack (Elasticsearch, Logstash, Kibana)
 * - Prometheus metrics
 * - Grafana dashboards
 * 
 * Order: 1 (Execute late, after request processing)
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();
        
        String method = request.getMethod().toString();
        String path = request.getPath().value();
        String correlationId = request.getHeaders().getFirst("X-Correlation-Id");
        
        log.info("Incoming request: method={}, path={}, correlationId={}", 
            method, path, correlationId);
        
        // Continue the filter chain and log after response
        return chain.filter(exchange)
            .then(Mono.fromRunnable(() -> {
                ServerHttpResponse response = exchange.getResponse();
                long duration = System.currentTimeMillis() - startTime;
                HttpStatus statusCode = (HttpStatus) response.getStatusCode();
                
                log.info("Completed request: method={}, path={}, status={}, duration={}ms, correlationId={}", 
                    method, path, statusCode != null ? statusCode.value() : "UNKNOWN", 
                    duration, correlationId);
                
                // Performance warning if request is slow
                if (duration > 1000) {
                    log.warn("Slow request detected: method={}, path={}, duration={}ms, correlationId={}", 
                        method, path, duration, correlationId);
                }
            }))
            .onErrorResume(error -> {
                long duration = System.currentTimeMillis() - startTime;
                log.error("Request failed: method={}, path={}, duration={}ms, correlationId={}, error={}", 
                    method, path, duration, correlationId, error.getMessage());
                return Mono.error(error);
            });
    }

    @Override
    public int getOrder() {
        return 1; // Low priority - execute late (after processing)
    }
}
