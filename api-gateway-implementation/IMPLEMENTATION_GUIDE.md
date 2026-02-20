# API Gateway - Implementation Guide

## 📖 Table of Contents

1. [Architecture Deep Dive](#architecture-deep-dive)
2. [Component Explanations](#component-explanations)
3. [Request Flow](#request-flow)
4. [Configuration Details](#configuration-details)
5. [Filters Execution Order](#filters-execution-order)
6. [JWT Validation Flow](#jwt-validation-flow)
7. [Rate Limiting Implementation](#rate-limiting-implementation)
8. [Observability Setup](#observability-setup)
9. [Troubleshooting](#troubleshooting)
10. [Interview Questions & Answers](#interview-questions--answers)

---

## 🏗️ Architecture Deep Dive

### Why Spring Cloud Gateway?

**Not Zuul 1 (Servlet-based, blocking)**:
- Thread per request model
- Limited by thread pool size (200-500 concurrent requests)
- Thread exhaustion under load

**Spring Cloud Gateway (Reactive, non-blocking)**:
- Event loop model (8 threads handle 10,000+ requests)
- Built on WebFlux + Netty
- Asynchronous I/O

### Performance Comparison

```
Scenario: 50,000 students taking exam simultaneously
Backend latency: 2 seconds per request

Zuul 1 (200 thread pool):
- Request 1-200: Processed immediately
- Request 201-50000: BLOCKED, waiting for threads
- Gateway becomes bottleneck

Spring Cloud Gateway (8 threads):
- All 50,000 requests accepted
- Event loop distributes work
- No blocking
- Total throughput: 10,000+ req/sec
```

---

## 🧩 Component Explanations

### 1. ApiGatewayApplication.java

**Purpose**: Bootstrap the Spring Boot application

**Why minimal?**
- Gateway is NOT a business service
- No `@RestController`, no `@Service`, no `@Repository`
- Just routing + filters

### 2. SecurityConfig.java

**Purpose**: Configure JWT validation

**Key Decisions**:

```java
.csrf(csrf -> csrf.disable())
```
Why? Stateless API. CSRF protection not needed for JWT-based auth.

```java
.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
```
Why? Don't create sessions. Gateway is stateless.

```java
.pathMatchers("/api/auth/login").permitAll()
```
Why? Login endpoint is public (no JWT yet).

```java
.anyExchange().authenticated()
```
Why? All other endpoints require JWT.

```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))
```
Why? Validates JWT signature using public key from Auth Service.

### 3. CorrelationIdFilter.java

**Purpose**: Generate unique ID for distributed tracing

**Why important?**

Without correlation ID:
```
[Gateway] Request received
[Exam Service] Processing submit
[Evaluation Service] Scoring exam
[Result Service] ERROR: Database timeout

Question: Which request failed?
Answer: NO IDEA. Can't correlate logs.
```

With correlation ID:
```
[Gateway] correlation-id=abc-123 | Request received
[Exam Service] correlation-id=abc-123 | Processing submit
[Evaluation Service] correlation-id=abc-123 | Scoring exam
[Result Service] correlation-id=abc-123 | ERROR: Database timeout

grep "correlation-id=abc-123" → See entire request flow!
```

**Order**: `-1` (High priority - execute early)

### 4. LoggingFilter.java

**Purpose**: Log request/response with performance metrics

**Why log at gateway?**

```
User Service logs: "Processed request in 50ms"
→ Only User Service processing time

Gateway logs: "Request completed in 145ms"
→ TOTAL time including:
   - Network latency
   - JWT validation
   - Routing
   - User Service processing
   - Response serialization
```

**Order**: `1` (Low priority - execute late, after processing)

### 5. JwtUserInfoFilter.java

**Purpose**: Extract user info from JWT and forward to backend services

**Flow**:
1. JWT already validated by `SecurityConfig`
2. Extract claims: `sub` (userId), `email`, `roles`
3. Add headers:
   ```
   X-User-Id: user-123
   X-User-Email: student@exam.com
   X-User-Roles: STUDENT
   ```
4. Backend services read these headers

**Why?**
- Backend services don't need JWT libraries
- Backend services don't need public keys
- Simpler backend code
- Performance: Validate once, not 6 times

**Security**:
- Headers added AFTER JWT validation
- Backend services should ONLY accept requests from gateway
- Use Kubernetes NetworkPolicy to enforce this

**Order**: `0` (Medium priority - after JWT validation, before routing)

### 6. RateLimitConfig.java

**Purpose**: Configure rate limiting key resolver

**Problem**:
```
Gateway Instance 1: User sends 100 requests → Tracked in-memory
Gateway Instance 2: Same user sends 100 requests → Tracked in-memory

Result: User bypassed 100 req/min limit!
```

**Solution**: Redis
```
Redis Key: "rate-limit:user-123:2024-02-15-10:30"
Value: 145 requests

Both gateway instances see same counter
```

**Key Resolution**:
- Authenticated: Use `X-User-Id` from JWT
- Public: Use IP address

### 7. GlobalExceptionHandler.java

**Purpose**: Handle errors and return consistent responses

**Common Errors**:
- `401 Unauthorized`: JWT invalid/expired
- `403 Forbidden`: JWT valid but insufficient permissions
- `429 Too Many Requests`: Rate limit exceeded
- `502 Bad Gateway`: Backend service down
- `504 Gateway Timeout`: Backend service slow

**Response Format**:
```json
{
  "timestamp": "2024-02-15T10:30:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again later.",
  "path": "/api/exams/123",
  "correlationId": "abc-123"
}
```

---

## 🔄 Request Flow

### Public Endpoint (Login)

```
1. Frontend → POST /api/auth/login
2. Gateway CorrelationIdFilter → Add X-Correlation-Id
3. Gateway SecurityConfig → Check .permitAll() → Allow
4. Gateway LoggingFilter → Log start time
5. Gateway → Route to Auth Service
6. Auth Service → Validate credentials → Return JWT
7. Gateway LoggingFilter → Log duration
8. Gateway → Frontend (JWT in response)
```

### Protected Endpoint (Get User)

```
1. Frontend → GET /api/users/123
   Authorization: Bearer <JWT>

2. Gateway CorrelationIdFilter → Add X-Correlation-Id

3. Gateway SecurityConfig → Validate JWT
   - Decode JWT
   - Verify signature (using Auth Service public key)
   - Check expiration
   - If invalid → 401 Unauthorized

4. Gateway JwtUserInfoFilter → Extract user info
   - Read JWT claims: sub, email, roles
   - Add headers: X-User-Id, X-User-Email, X-User-Roles

5. Gateway RateLimitFilter → Check rate limit
   - Key: "rate-limit:user-123:2024-02-15-10:30"
   - INCR count in Redis
   - If count > limit → 429 Too Many Requests

6. Gateway LoggingFilter → Log start time

7. Gateway → Route to User Service
   Headers:
   - X-Correlation-Id: abc-123
   - X-User-Id: user-123
   - X-User-Email: student@exam.com
   - X-User-Roles: STUDENT

8. User Service → Process request
   - Trust gateway headers (no JWT re-validation)
   - Check X-User-Id matches path param
   - Return user data

9. Gateway LoggingFilter → Log duration

10. Gateway → Frontend (user data in response)
```

---

## ⚙️ Configuration Details

### Route Configuration

```yaml
routes:
  - id: user-service
    uri: http://user-service:8080
    predicates:
      - Path=/api/users/**
    filters:
      - StripPrefix=1
```

**Breakdown**:
- `id`: Unique route identifier
- `uri`: Backend service URL (Kubernetes DNS)
- `predicates`: Matching conditions
  - `Path=/api/users/**` → Matches `/api/users/123`, `/api/users/profile`, etc.
- `filters`: Transformations
  - `StripPrefix=1` → Remove `/api` before forwarding
    - Gateway receives: `/api/users/123`
    - User Service receives: `/users/123`

### Environment-Based Configuration

**Development** (`application-dev.yml`):
```yaml
AUTH_SERVICE_URL: http://localhost:8081
```

**Production** (`application-prod.yml`):
```yaml
AUTH_SERVICE_URL: http://auth-service:8080
```

**Kubernetes DNS**:
- `auth-service` resolves to Kubernetes Service
- Kubernetes Service load-balances across pods
- No Eureka/Consul needed

---

## 📊 Filters Execution Order

### Order Values

| Filter | Order | Phase |
|--------|-------|-------|
| CorrelationIdFilter | -1 | Pre (Early) |
| JwtUserInfoFilter | 0 | Pre (Medium) |
| LoggingFilter | 1 | Post (Late) |

### Execution Flow

```
1. CorrelationIdFilter (Order: -1) → PRE
   ↓ Add X-Correlation-Id
   
2. Spring Security → JWT Validation
   ↓ Validate JWT signature
   
3. JwtUserInfoFilter (Order: 0) → PRE
   ↓ Extract user info, add headers
   
4. Route to Backend Service
   ↓
   
5. Backend Service Processes Request
   ↓
   
6. LoggingFilter (Order: 1) → POST
   ↓ Log response time
```

**Why this order?**

- **Correlation ID first**: All subsequent logs need it
- **JWT validation second**: Security before everything
- **User info extraction third**: Needs validated JWT
- **Logging last**: Needs complete response

---

## 🔐 JWT Validation Flow

### JWT Structure

```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user-123",
    "email": "student@exam.com",
    "roles": "STUDENT",
    "iss": "http://localhost:8081",
    "exp": 1708000000
  },
  "signature": "<RSA signature>"
}
```

### Validation Steps

1. **Decode JWT**
   - Base64 decode header, payload, signature

2. **Verify Signature**
   - Get public key from Auth Service (JWK Set)
   - Verify signature using public key
   - If invalid → 401 Unauthorized

3. **Check Expiration**
   - Compare `exp` claim with current time
   - If expired → 401 Unauthorized

4. **Check Issuer**
   - Compare `iss` claim with configured issuer
   - If mismatch → 401 Unauthorized

5. **Extract Claims**
   - Read `sub`, `email`, `roles`

### Configuration

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://auth-service:8080/auth/.well-known/jwks.json
          issuer-uri: http://auth-service:8080
```

**JWK Set Endpoint**:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "n": "<modulus>",
      "e": "<exponent>",
      "alg": "RS256",
      "kid": "key-1"
    }
  ]
}
```

Gateway fetches public key once, caches it.

---

## 🚦 Rate Limiting Implementation

### Algorithm: Token Bucket

```
Bucket capacity: 20 tokens
Replenish rate: 10 tokens/second

Request arrives:
  - If tokens available: Consume 1 token, allow request
  - If no tokens: Reject with 429 Too Many Requests
  
Tokens replenish over time (10/sec)
```

### Redis Implementation

```
Key: rate-limit:user-123:2024-02-15-10:30
Value: 15 (current request count)
TTL: 60 seconds

Request arrives:
  count = INCR rate-limit:user-123:2024-02-15-10:30
  if count == 1:
    EXPIRE rate-limit:user-123:2024-02-15-10:30 60
  if count > 20:
    return 429
```

### Configuration Per Route

```yaml
routes:
  - id: exam-service
    filters:
      - name: RequestRateLimiter
        args:
          redis-rate-limiter.replenishRate: 20
          redis-rate-limiter.burstCapacity: 40
```

- `replenishRate`: Tokens per second
- `burstCapacity`: Maximum burst

---

## 📈 Observability Setup

### Actuator Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Overall health status |
| `/actuator/health/liveness` | Is app running? (K8s liveness probe) |
| `/actuator/health/readiness` | Is app ready for traffic? (K8s readiness probe) |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/metrics` | Raw metrics |
| `/actuator/gateway/routes` | List configured routes |

### Prometheus Metrics

```promql
# Request rate
rate(http_server_requests_seconds_count[1m])

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[1m])

# 95th percentile latency
histogram_quantile(0.95, http_server_requests_seconds_bucket)

# Gateway route stats
spring_cloud_gateway_requests_seconds_count
```

### Grafana Dashboard Panels

1. **Request Rate** (Graph)
2. **Error Rate** (Graph)
3. **Latency** (Heatmap)
4. **Active Routes** (Stat)
5. **Redis Connection** (Stat)

---

## 🐛 Troubleshooting

### Issue: 401 Unauthorized

**Symptoms**: All protected endpoints return 401

**Causes**:
1. JWT signature invalid
2. Auth Service public key not accessible
3. JWT expired

**Debug**:
```bash
# Check JWT validation logs
kubectl logs -f api-gateway-pod | grep JWT

# Test Auth Service JWK endpoint
curl http://auth-service:8080/auth/.well-known/jwks.json

# Decode JWT (jwt.io)
```

### Issue: 429 Too Many Requests

**Symptoms**: Requests blocked after threshold

**Causes**:
1. Rate limit too low
2. Redis connection lost
3. Multiple users sharing IP (public endpoints)

**Debug**:
```bash
# Check Redis connection
kubectl exec -it api-gateway-pod -- wget -qO- http://localhost:8080/actuator/health

# Check Redis keys
redis-cli --scan --pattern "rate-limit:*"

# Increase rate limit in application.yml
```

### Issue: 502 Bad Gateway

**Symptoms**: Gateway can't reach backend service

**Causes**:
1. Backend service down
2. Wrong service URL
3. Network policy blocking

**Debug**:
```bash
# Check backend service status
kubectl get pods -n exam-platform

# Test connectivity from gateway pod
kubectl exec -it api-gateway-pod -- wget -qO- http://user-service:8080/actuator/health

# Check service DNS
kubectl exec -it api-gateway-pod -- nslookup user-service
```

---

## 💡 Interview Questions & Answers

### Q1: Why Spring Cloud Gateway over NGINX?

**Answer**:
"NGINX is an infrastructure-level reverse proxy, excellent for TLS termination and static content. However, for Spring Boot microservices, Spring Cloud Gateway provides:

1. Native Spring integration (Security, Actuator, Config)
2. Application-level routing intelligence
3. JWT validation without Lua scripts
4. Easier to write custom filters in Java vs Lua

In production, we often use both: NGINX frontend for SSL + DDoS, Spring Cloud Gateway for application logic."

### Q2: How do you handle JWT validation at scale?

**Answer**:
"JWT validation happens once at the gateway. We validate the signature using the Auth Service's public key, extract user information, and forward it to backend services via headers (X-User-Id, X-User-Email, X-User-Roles).

Backend services trust the gateway and don't re-validate JWT. This gives us:
- Performance: Validate once, not 6 times across services
- Simpler backends: No JWT libraries needed
- Security: Centralized validation logic

We enforce this trust with Kubernetes NetworkPolicy—backends only accept traffic from the gateway."

### Q3: Explain correlation ID and its importance

**Answer**:
"Correlation ID is a unique identifier generated for each request at the gateway. It's propagated to all downstream services via the X-Correlation-Id header.

Without it, debugging is impossible:
- Student reports: 'I didn't get my exam result'
- Logs show Exam Service processed something
- Evaluation Service shows errors
- Can't connect them

With correlation ID:
- grep 'correlation-id=abc-123' across all service logs
- See the exact request flow
- Identify where it failed

This is essential for production debugging in distributed systems."

### Q4: How does rate limiting work with multiple gateway instances?

**Answer**:
"We use Redis for shared state. The key is: rate-limit:<userId>:<currentMinute>

Without Redis:
- Instance 1 tracks 100 requests
- Instance 2 tracks 100 requests
- User bypassed the limit

With Redis:
- Both instances increment the same counter
- Redis INCR is atomic (no race conditions)
- Sub-millisecond latency
- Auto-expiration via TTL

This ensures rate limits work correctly across horizontally scaled gateway instances."

### Q5: Why is the gateway stateless?

**Answer**:
"Statelessness enables horizontal scaling in Kubernetes:

1. No in-memory sessions (use JWT tokens)
2. No local caches (use Redis for rate limiting)
3. Configuration from environment variables

This means:
- If Instance 1 crashes, Instance 2 handles requests seamlessly
- Scale from 3 to 10 replicas without coordination
- Zero downtime deployments (rolling updates)

Stateless = cloud-native. The gateway trusts the infrastructure (Kubernetes, Redis) for state management."

---

## 🎯 Key Takeaways

1. **Gateway routes, doesn't compute**: Business logic stays in domain services
2. **Validate once, trust everywhere**: JWT validation at gateway, backends trust headers
3. **Correlation ID is non-negotiable**: Essential for distributed tracing
4. **Redis for shared state**: Rate limiting across instances
5. **Stateless = scalable**: Horizontal scaling without coordination
6. **Observability from day one**: Metrics, logs, traces

---

**End of Implementation Guide**
