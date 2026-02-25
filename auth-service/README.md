# Auth Service — OAuth2.0 Authentication Microservice

**Online Examination & Assessment Platform**

Spring Boot microservice implementing OAuth2.0 authentication with JWT access tokens
and opaque refresh tokens. Acts as the Auth Server in the microservices architecture.

---

## Architecture Role

```
[Client] → [API Gateway :8080] → [Auth Service :8081]
                                        ↓
                               [User Service :8082]  (credential validation)
                                        ↓
                                   [Redis]           (refresh tokens, rate limits, blocklist)
                                   [PostgreSQL]      (audit log)
```

API Gateway validates JWTs for all downstream services.
Auth Service is only called for: login, refresh, logout, register.

---

## OAuth2.0 Grant Types Implemented

| Grant Type | Endpoint | Use Case |
|---|---|---|
| Resource Owner Password Credentials (ROPC) | `POST /api/auth/login` | Student/Admin login with email+password |
| Refresh Token | `POST /api/auth/refresh` | Silent token renewal during active exam |
| Token Revocation (RFC 7009) | `POST /api/auth/logout` | Explicit logout |

---

## Token Design

### Access Token (JWT)
- Algorithm: **HMAC-SHA256** (hardcoded — never negotiated)
- Expiry: **15 minutes** (configurable)
- Claims: `userId`, `email`, `role`, `firstName`, `jti` (unique ID for blocklist)
- Validated cryptographically by API Gateway — no DB call needed

### Refresh Token (Opaque UUID)
- **NOT a JWT** — intentional design decision
- Random UUID stored in **Redis** with TTL
- Immediately revocable: delete Redis key = token invalid everywhere
- Rotated on every use (security against stolen tokens)

> **Why opaque UUID for refresh?**
> JWT = self-contained = unrevokable until expiry.
> If we used JWT as refresh token, logout would be broken for up to 7 days.
> Opaque UUID gives us full server-side control of validity.

---

## Security Features

| Feature | Implementation |
|---|---|
| Brute force protection | Redis rate limiting per email (5 attempts / 15 min window) |
| User enumeration prevention | Same error message for bad email and bad password |
| Refresh token rotation | Every refresh destroys old token, issues new one |
| Access token revocation | Redis blocklist by jti on logout |
| User re-validation on refresh | User Service called on every refresh (detects locked accounts) |
| Max concurrent sessions | Configurable per user (default: 5 devices) |
| Audit logging | Every auth event logged async to PostgreSQL |
| Algorithm confusion prevention | jjwt enforces HS256 via key type — rejects alg:none |
| Secret management | Injected from Kubernetes Secret, never in code |

---

## Endpoints

```
POST   /api/auth/login        Login (ROPC grant)
POST   /api/auth/refresh      Refresh token grant
POST   /api/auth/logout       Token revocation
POST   /api/auth/register     User registration + auto-login
GET    /api/auth/sessions     List active sessions
DELETE /api/auth/sessions     Logout from all devices
GET    /actuator/health       Kubernetes liveness/readiness probes
```

### Login Request/Response
```json
// POST /api/auth/login
{
  "email": "student@exam.com",
  "password": "Secret123!",
  "deviceInfo": "Chrome/Desktop"
}

// 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "scope": "read write"
}
```

---

## Project Structure

```
src/main/java/com/examplatform/auth/
├── AuthServiceApplication.java
├── config/
│   ├── JwtProperties.java       Strongly-typed JWT config (validated at startup)
│   ├── SecurityConfig.java      Spring Security setup (stateless, no CSRF, BCrypt 12)
│   ├── RedisConfig.java         Lettuce connection pool config
│   ├── WebClientConfig.java     HTTP client for User Service (with timeouts)
│   └── AsyncConfig.java         Thread pool for async audit logging
├── controller/
│   └── AuthController.java      REST endpoints
├── service/
│   ├── AuthService.java         Core OAuth2.0 grant type logic
│   ├── RateLimitService.java    Redis sliding-window rate limiting
│   ├── TokenBlacklistService.java  Access token revocation
│   └── AuditLogService.java     Async security event logging
├── client/
│   └── UserServiceClient.java   Circuit-breaker-protected WebClient
├── util/
│   └── JwtTokenProvider.java    JWT signing and validation
├── entity/
│   ├── RefreshTokenEntity.java  Redis entity (opaque UUID + TTL)
│   └── AuthAuditLog.java        PostgreSQL audit log entity
├── repository/
│   ├── RefreshTokenRepository.java  Spring Data Redis
│   └── AuthAuditLogRepository.java  Spring Data JPA
├── security/
│   ├── AuthEntryPoint.java      Custom 401 JSON response
│   └── UserDetailsServiceImpl.java
├── filter/
│   └── RateLimitFilter.java     Catches rate limit exceptions in filter chain
├── exception/
│   ├── AuthExceptions.java      All domain exceptions
│   └── GlobalExceptionHandler.java  Maps exceptions to HTTP responses
└── dto/
    └── AuthDtos.java            All request/response records

src/main/resources/
├── application.yml
└── db/migration/
    └── V1__create_auth_audit_log.sql

k8s/
└── auth-service.yaml            Deployment, Service, HPA, NetworkPolicy, Secrets
```

---

## Running Locally

### Prerequisites
- Docker (for Redis + PostgreSQL)
- Java 17+

### Start Dependencies
```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
docker run -d --name postgres \
  -e POSTGRES_DB=auth_db \
  -e POSTGRES_USER=auth_user \
  -e POSTGRES_PASSWORD=auth_pass \
  -p 5432:5432 postgres:15-alpine
```

### Run
```bash
export JWT_SECRET="local-dev-secret-minimum-32-characters"
./mvnw spring-boot:run
```

### Test Login
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"student@exam.com","password":"Secret123!"}'
```

---

## Configuration Reference

| Property | Env Variable | Default | Description |
|---|---|---|---|
| `jwt.secret` | `JWT_SECRET` | **Required** | HMAC-SHA256 signing key (min 32 chars) |
| `jwt.access-token.expiry-ms` | `JWT_ACCESS_EXPIRY_MS` | `900000` (15 min) | Access token lifetime |
| `jwt.refresh-token.expiry-ms` | `JWT_REFRESH_EXPIRY_MS` | `604800000` (7 days) | Refresh token lifetime |
| `jwt.refresh-token.max-per-user` | `JWT_MAX_SESSIONS` | `5` | Max concurrent sessions |
| `services.user-service.url` | `USER_SERVICE_URL` | `http://user-service:8082` | User Service URL |
| `auth.rate-limit.login.max-attempts` | `RATE_LIMIT_MAX` | `5` | Max login attempts per window |
| `auth.rate-limit.login.window-seconds` | `RATE_LIMIT_WINDOW` | `900` | Rate limit window (seconds) |

---

## JWT Secret Rotation (Zero-Downtime)

To rotate the JWT secret without downtime:
1. Add a `kid` (Key ID) claim to issued JWTs
2. Gateway checks `kid` and selects the correct verification key
3. Deploy new Auth Service pods with new secret (old pods keep old secret)
4. Old tokens expire naturally (within 15 minutes)
5. All new tokens use new secret
6. Remove old secret from configuration

See `JwtTokenProvider` comments for implementation guidance.
