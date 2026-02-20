# API Gateway - Production-Ready Implementation

## 🎯 Overview

This is a **production-ready API Gateway** for the **Online Examination & Assessment Platform**, designed following enterprise best practices and cloud-native principles.

### Architecture Positioning

The API Gateway is the **single entry point** for all client requests, serving as the architectural control point of the distributed system.

```
Frontend (React/Angular)
         ↓
    API Gateway (Port 8080)
    ├── JWT Validation
    ├── Correlation ID Generation
    ├── Rate Limiting (Redis)
    ├── Logging & Metrics
    └── Routing
         ↓
    ├─→ Auth Service (8081)
    ├─→ User Service (8082)
    ├─→ Exam Service (8083)
    ├─→ Evaluation Service (8084)
    ├─→ Result Service (8085)
    └─→ Notification Service (8086)
```

---

## 🏗️ Technology Stack

| Component | Technology | Why? |
|-----------|-----------|------|
| **Gateway Framework** | Spring Cloud Gateway | Reactive, non-blocking (WebFlux + Netty). Handles 10,000+ req/sec |
| **Security** | Spring Security + OAuth2 JWT | Validates JWT at gateway. Backend services trust gateway |
| **Rate Limiting** | Redis | Shared state across instances. Atomic operations, <1ms latency |
| **Observability** | Actuator + Prometheus | Health checks, metrics, distributed tracing |
| **Container** | Docker | Lightweight JRE image with non-root user |
| **Orchestration** | Kubernetes | Horizontal scaling, health probes, service discovery |

---

## ✨ Features

### 1. **Reactive & Non-Blocking**
- Built on **Spring WebFlux** and **Netty**
- Event loop model: 8 threads handle thousands of concurrent requests
- **10x throughput** vs servlet-based gateways (Zuul 1)

### 2. **JWT Validation**
- **Single point of security enforcement**
- Validates JWT signature using Auth Service public key
- Extracts user info and forwards via headers:
  - `X-User-Id`: User identifier
  - `X-User-Email`: User email
  - `X-User-Roles`: User roles
- Backend services trust the gateway (no re-validation)

### 3. **Correlation ID for Distributed Tracing**
- Every request gets a unique `X-Correlation-Id`
- Propagated to all backend services
- Enables end-to-end request tracing across microservices
- Essential for debugging production issues

### 4. **Rate Limiting with Redis**
- Prevents abuse (DOS attacks, accidental loops)
- Shared state across multiple gateway instances
- Configurable per route:
  - User Service: 10 req/sec, burst 20
  - Exam Service: 20 req/sec, burst 40
- Returns `429 Too Many Requests` when limit exceeded

### 5. **Comprehensive Logging**
- Logs every request/response with:
  - Method, path, status code
  - Response time (end-to-end latency)
  - Correlation ID for tracing
- Performance warnings for slow requests (>1000ms)

### 6. **Observability & Metrics**
- **Health Endpoints**: `/actuator/health/liveness`, `/actuator/health/readiness`
- **Prometheus Metrics**: `/actuator/prometheus`
- Tracks:
  - Request rate, error rate, latency (95th percentile)
  - JVM metrics (memory, GC, threads)
  - Gateway-specific metrics (route stats)

### 7. **Kubernetes-Ready**
- **Stateless**: No in-memory sessions. Redis for shared state
- **Horizontal Scaling**: Auto-scales 3-10 pods based on CPU/memory
- **Health Probes**: Liveness (restart if dead) + Readiness (remove from LB if not ready)
- **Graceful Shutdown**: Drains connections before terminating

---

## 📁 Project Structure

```
api-gateway/
├── src/main/java/com/examplatform/gateway/
│   ├── ApiGatewayApplication.java          # Main application
│   ├── config/
│   │   ├── SecurityConfig.java             # JWT validation
│   │   └── RateLimitConfig.java            # Rate limiting key resolver
│   ├── filter/
│   │   ├── CorrelationIdFilter.java        # Distributed tracing
│   │   ├── LoggingFilter.java              # Request/response logging
│   │   └── JwtUserInfoFilter.java          # Extract JWT user info
│   └── exception/
│       └── GlobalExceptionHandler.java     # Error handling
├── src/main/resources/
│   ├── application.yml                     # Base configuration
│   ├── application-dev.yml                 # Development config
│   └── application-prod.yml                # Production config
├── kubernetes/
│   └── deployment.yml                      # K8s manifests
├── Dockerfile                              # Multi-stage Docker build
└── pom.xml                                 # Maven dependencies
```

### Why This Structure?

**❌ NO Controllers, Services, Repositories, Entities**

The gateway is **NOT a business service**. It's a **routing layer**.

- **Separation of concerns**: Business logic belongs in domain services
- **Scalability**: Gateway scales based on request volume, not business complexity
- **Reliability**: Simpler gateway = lower failure risk

**✅ Only Configuration + Filters**

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+**
- **Maven 3.9+**
- **Docker** (for containerization)
- **Redis** (for rate limiting)
- **Kubernetes** (for production deployment)

### Local Development Setup

#### 1. Start Redis (Required for Rate Limiting)

```bash
docker run -d -p 6379:6379 redis:7-alpine
```

#### 2. Run the Gateway

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The gateway will start on **http://localhost:8080**

#### 3. Test Health Endpoint

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "ping": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

---

## 🔧 Configuration

### Environment Variables

| Variable | Description | Default (Dev) | Production |
|----------|-------------|---------------|------------|
| `AUTH_SERVICE_URL` | Auth Service endpoint | `http://localhost:8081` | `http://auth-service:8080` |
| `USER_SERVICE_URL` | User Service endpoint | `http://localhost:8082` | `http://user-service:8080` |
| `EXAM_SERVICE_URL` | Exam Service endpoint | `http://localhost:8083` | `http://exam-service:8080` |
| `REDIS_HOST` | Redis hostname | `localhost` | `redis-service` |
| `REDIS_PORT` | Redis port | `6379` | `6379` |
| `REDIS_PASSWORD` | Redis password | `` | `<from-secret>` |
| `JWT_ISSUER` | JWT issuer URI | `http://localhost:8081` | `https://examplatform.com` |

### Profiles

- **dev**: Local development with localhost URLs
- **prod**: Production with Kubernetes DNS

Activate profile:
```bash
export SPRING_PROFILES_ACTIVE=prod
```

---

## 🐳 Docker Build

### Build Image

```bash
docker build -t examplatform/api-gateway:1.0.0 .
```

### Run Container

```bash
docker run -d \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e REDIS_HOST=host.docker.internal \
  examplatform/api-gateway:1.0.0
```

---

## ☸️ Kubernetes Deployment

### Prerequisites

1. Kubernetes cluster (v1.24+)
2. `kubectl` configured
3. Backend services deployed
4. Redis deployed

### Deploy

```bash
# Create namespace
kubectl create namespace exam-platform

# Apply manifests
kubectl apply -f kubernetes/deployment.yml

# Check status
kubectl get pods -n exam-platform
kubectl get svc -n exam-platform
```

### Scaling

```bash
# Manual scaling
kubectl scale deployment api-gateway --replicas=5 -n exam-platform

# Auto-scaling is configured via HPA (3-10 replicas based on CPU/memory)
kubectl get hpa -n exam-platform
```

### Health Checks

```bash
# Liveness probe
kubectl exec -it <pod-name> -n exam-platform -- \
  wget -qO- http://localhost:8080/actuator/health/liveness

# Readiness probe
kubectl exec -it <pod-name> -n exam-platform -- \
  wget -qO- http://localhost:8080/actuator/health/readiness
```

---

## 📊 Monitoring & Observability

### Prometheus Metrics

Gateway exposes Prometheus metrics at:
```
http://localhost:8080/actuator/prometheus
```

Key metrics:
- `http_server_requests_seconds_count`: Request count
- `http_server_requests_seconds_sum`: Total response time
- `http_server_requests_seconds_max`: Max response time

### Grafana Dashboard

Create Grafana dashboard with:

**Request Rate**:
```promql
rate(http_server_requests_seconds_count[1m])
```

**Error Rate**:
```promql
rate(http_server_requests_seconds_count{status=~"5.."}[1m])
```

**95th Percentile Latency**:
```promql
histogram_quantile(0.95, http_server_requests_seconds_bucket)
```

### Log Aggregation

Logs are JSON-formatted and include:
- `traceId`, `spanId`: Distributed tracing IDs
- `correlationId`: Request correlation ID
- `level`, `message`: Log level and message

Ship to ELK stack or Loki for aggregation.

---

## 🧪 Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```

### Load Testing

Use **Apache JMeter** or **k6**:

```bash
# Example with k6
k6 run --vus 1000 --duration 60s load-test.js
```

Expected throughput: **10,000+ req/sec** on 4-core, 8GB RAM instance.

---

## 🔒 Security Considerations

### 1. **JWT Validation**
- Gateway validates JWT signature
- Backend services trust gateway headers (`X-User-Id`, `X-User-Email`, `X-User-Roles`)
- **Important**: Backend services should ONLY accept requests from the gateway
- Use **network policies** or **service mesh** to enforce this

### 2. **Rate Limiting**
- Per-user rate limits prevent abuse
- Redis ensures shared state across instances
- Adjust limits per route in `application.yml`

### 3. **CORS**
- Configured in `application.yml`
- Production: Only allow `https://examplatform.com`
- Development: Allow localhost origins

### 4. **No Secrets in Code**
- Redis password from Kubernetes Secret
- JWT public key fetched from Auth Service
- Environment-specific configs in profiles

---

## 🎤 Interview-Ready Summary

> **"The API Gateway is the single entry point for all client requests in our microservices architecture. It provides routing intelligence, centralizes cross-cutting concerns like security and rate limiting, and acts as the aggregation point for observability.**
>
> **I chose Spring Cloud Gateway because it's reactive and non-blocking, built on WebFlux and Netty, giving us 10x throughput compared to servlet-based gateways. It integrates natively with Spring Security for JWT validation, Spring Actuator for metrics, and Kubernetes DNS for service discovery.**
>
> **JWT validation happens at the gateway. Backend services receive trusted headers (X-User-Id, X-User-Roles) and don't need to re-validate tokens. Rate limiting uses Redis for shared state across gateway instances. Correlation IDs enable distributed tracing—we can grep logs across all services to trace a single request.**
>
> **In production, the gateway is stateless and horizontally scalable in Kubernetes. We run 3-10 replicas with liveness and readiness probes. Prometheus metrics expose request rates, error rates, and 95th percentile latency.**
>
> **The key principle: the gateway routes, it doesn't compute. Business logic stays in domain services. The gateway remains thin, fast, and reliable."**

---

## 📚 Additional Resources

- [Spring Cloud Gateway Docs](https://spring.io/projects/spring-cloud-gateway)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Kubernetes Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)
- [Prometheus Monitoring](https://prometheus.io/docs/introduction/overview/)

---

## 👨‍💻 Author

**Senior Architect**  
Production-ready implementation for interview preparation and real-world deployment.

---

## 📄 License

This project is provided as educational material for interview preparation and learning purposes.
