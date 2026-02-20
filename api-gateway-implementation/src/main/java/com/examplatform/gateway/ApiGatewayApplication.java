package com.examplatform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application
 * 
 * Production-ready gateway for Online Examination Platform
 * - Reactive & Non-blocking (Spring Cloud Gateway)
 * - JWT Validation at the gateway
 * - Correlation ID for distributed tracing
 * - Rate limiting with Redis
 * - Prometheus metrics
 * - Kubernetes-ready (stateless, health probes)
 * 
 * @author Senior Architect
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
