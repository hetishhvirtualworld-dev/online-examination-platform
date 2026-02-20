# API Gateway - Quick Reference Cheat Sheet

## 🚀 Commands

### Local Development
```bash
# Quick start (everything)
./quick-start.sh

# Manual steps
docker run -d -p 6379:6379 redis:7-alpine
mvn clean package
SPRING_PROFILES_ACTIVE=dev java -jar target/api-gateway-*.jar

# Test health
curl http://localhost:8080/actuator/health
```

### Docker
```bash
# Build
docker build -t examplatform/api-gateway:1.0.0 .

# Run
docker run -p 8080:8080 examplatform/api-gateway:1.0.0

# Compose
docker-compose up -d
```

### Kubernetes
```bash
# Deploy
kubectl apply -f kubernetes/deployment.yml

# Status
kubectl get pods -n exam-platform
kubectl logs -f <pod-name> -n exam-platform

# Scale
kubectl scale deployment api-gateway --replicas=5 -n exam-platform
```

---

## 📁 File Quick Reference

| File | What It Does |
|------|--------------|
| `SecurityConfig.java` | JWT validation |
| `CorrelationIdFilter.java` | Generate trace IDs |
| `LoggingFilter.java` | Log performance |
| `JwtUserInfoFilter.java` | Extract user from JWT |
| `RateLimitConfig.java` | Rate limit keys |
| `GlobalExceptionHandler.java` | Handle errors |
| `application.yml` | Routes + config |
| `application-dev.yml` | Localhost URLs |
| `application-prod.yml` | K8s DNS URLs |

---

## 🎯 Key Concepts (1-Minute Explanations)

### Why Spring Cloud Gateway?
**Reactive + Non-blocking** = 10x throughput vs Zuul 1

### JWT Validation
**Gateway validates → Backend trusts**  
Validate once, not 6 times

### Correlation ID
**One ID → Full trace across services**  
Essential for debugging

### Rate Limiting
**Redis = Shared state**  
Works across multiple instances

### Stateless
**No memory = Horizontal scaling**  
Add replicas anytime

---

## 🔧 Configuration Patterns

### Add a New Route
```yaml
routes:
  - id: new-service
    uri: http://new-service:8080
    predicates:
      - Path=/api/new/**
    filters:
      - StripPrefix=1
```

### Change Rate Limit
```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 50
      redis-rate-limiter.burstCapacity: 100
```

### Add Public Endpoint
```java
.pathMatchers("/api/public/**").permitAll()
```

---

## 📊 Monitoring

### Endpoints
- Health: `/actuator/health`
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Metrics: `/actuator/prometheus`
- Routes: `/actuator/gateway/routes`

### Prometheus Queries
```promql
# Request rate
rate(http_server_requests_seconds_count[1m])

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[1m])

# Latency P95
histogram_quantile(0.95, http_server_requests_seconds_bucket)
```

---

## 🐛 Troubleshooting

| Problem | Check |
|---------|-------|
| 401 Unauthorized | JWT config, Auth Service reachable? |
| 429 Too Many Requests | Rate limit config, Redis connected? |
| 502 Bad Gateway | Backend service up? |
| 504 Timeout | Backend too slow? |

### Debug Commands
```bash
# Check JWT
curl http://auth-service:8080/auth/.well-known/jwks.json

# Check Redis
redis-cli --scan --pattern "rate-limit:*"

# Check backend
kubectl exec -it gateway-pod -- wget -qO- http://user-service:8080/health
```

---

## 🎤 Interview Answers (30 Seconds Each)

### "Why Spring Cloud Gateway?"
"Reactive and non-blocking, built on WebFlux and Netty. Gives 10x throughput vs servlet-based gateways. Native Spring integration for JWT, metrics, and Kubernetes."

### "How does JWT work?"
"Gateway validates JWT signature using Auth Service public key. If valid, extracts userId and forwards via X-User-Id header. Backend services trust the gateway."

### "What's correlation ID?"
"Unique ID generated at gateway, propagated to all services. Enables end-to-end request tracing. Essential for debugging production issues across services."

### "Rate limiting with multiple instances?"
"Use Redis for shared state. Both instances increment same counter. Redis provides atomic operations and sub-millisecond latency."

### "Why stateless?"
"Enables horizontal scaling. No in-memory sessions, Redis for rate limits. If one instance crashes, others handle requests seamlessly."

---

## 📈 Expected Performance

- **Throughput**: 10,000+ req/sec per instance
- **Latency P95**: <100ms
- **Resources**: 250m CPU, 512Mi memory
- **Threads**: 8 (event loop)

---

## ✅ Pre-Production Checklist

- [ ] Configure production JWT issuer
- [ ] Set Redis password
- [ ] Configure CORS domains
- [ ] Review rate limits
- [ ] Set resource limits
- [ ] Configure HPA
- [ ] Set up monitoring
- [ ] Test liveness/readiness
- [ ] Load test
- [ ] Document runbooks

---

## 🎯 Architecture Principles

1. **Gateway routes, doesn't compute**
2. **Validate once, trust everywhere**
3. **Correlation ID is non-negotiable**
4. **Redis for shared state**
5. **Stateless = scalable**
6. **Observability from day one**

---

## 📚 Documentation Paths

- Quick start: `README.md`
- Deep dive: `IMPLEMENTATION_GUIDE.md`
- Overview: `PROJECT_OVERVIEW.md`
- This cheat sheet: `QUICK_REFERENCE.md`

---

## 🔗 Key URLs

**Local Dev**:
- Gateway: http://localhost:8080
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/prometheus

**Production** (adjust domain):
- Gateway: https://api.examplatform.com
- Health: https://api.examplatform.com/actuator/health

---

**Remember**: Architecture first, code second. Understanding the WHY makes you interview-ready.
