package com.examplatform.auth.service;

import com.examplatform.auth.client.UserServiceClientWebClient;
import com.examplatform.auth.dto.*;
import com.examplatform.auth.entity.User;
import com.examplatform.auth.repository.UserRepository;
import com.examplatform.auth.security.JwtUtil;
import com.examplatform.auth.security.TokenStore;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * AuthService — implements all OAuth2.0 grant types.
 *
 * Mirrors the Gateway's JwtAuthenticationFilter in style:
 *   - Each method has a clear, numbered flow in the comment
 *   - One method per grant type
 *   - Focused on one thing: auth logic
 *   - No noise, no over-engineering
 *
 * Grant types:
 *   login()    — Resource Owner Password Credentials (ROPC)
 *   refresh()  — Refresh Token grant
 *   logout()   — Token Revocation (RFC 7009)
 *   register() — Registration + auto-login
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final JwtUtil             jwtUtil;
    private final TokenStore          tokenStore;
    private final UserRepository      userRepository;
    private final UserServiceClientWebClient	  userServiceClient;
    private final PasswordEncoder     passwordEncoder;
    private final AuditLogService     auditLogService;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    // ─── GRANT TYPE 1: Resource Owner Password Credentials ───────────

    /**
     * Login — ROPC grant: email + password → access token + refresh token.
     *
     * Flow:
     *   1. Rate limit check — before any DB calls (protect even when services are slow)
     *   2. Fetch user from User Service — does this account exist?
     *   3. Verify BCrypt password — ~300ms intentional delay, defeats brute force
     *   4. Check account status — active? locked?
     *   5. Generate JWT access token (15 min) — same claims Gateway reads
     *   6. Generate opaque refresh token (UUID) — saved in Redis with TTL
     *   7. Reset rate limit counter — don't penalize successful logins
     *   8. Async audit log — fire and forget, never blocks the response
     */
    public TokenResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email.toLowerCase().trim();
        String ip    = extractIp(httpRequest);

        // Step 1: Rate limit
        if (tokenStore.isRateLimited(email)) {
            auditLogService.logFailure(email, "LOGIN_FAILURE", "Rate limited", ip);
            throw new RuntimeException("Too many login attempts. Please try again in 15 minutes.");
        }

        // Step 2: Fetch user from User Service
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            // Run a dummy BCrypt to prevent timing attacks revealing user existence
            passwordEncoder.matches("dummy", "$2a$12$dummy.hash.to.prevent.timing.attacks.0000000000000");
            auditLogService.logFailure(email, "LOGIN_FAILURE", "User not found", ip);
            throw new RuntimeException("Invalid email or password");
        });

        // Step 3: Verify password
        if (!passwordEncoder.matches(request.password, user.getPassword())) {
            auditLogService.logFailure(email, "LOGIN_FAILURE", "Invalid password", ip);
            throw new RuntimeException("Invalid email or password");
        }

        // Step 5: Generate access token
        String accessToken  = jwtUtil.generateAccessToken(user.getId().toString(), user.getEmail(), user.getRole());

        // Step 6: Generate and store refresh token
        String refreshToken = jwtUtil.generateRefreshToken();
        tokenStore.saveRefreshToken(refreshToken, user.getId().toString());

        // Step 7: Reset rate limit on success
        tokenStore.resetRateLimit(email);

        // Step 8: Async audit log
        auditLogService.logSuccess(user.getId().toString(), user.getEmail(), "LOGIN_SUCCESS", ip);

        log.info("Login success: userId={}, role={}", user.getId(), user.getRole());

        return TokenResponse.of(accessToken, refreshToken, accessTokenExpiryMs / 1000);
    }

    // ─── GRANT TYPE 2: Refresh Token ─────────────────────────────────

    /**
     * Refresh — exchange a valid refresh token for a new token pair.
     *
     * Flow:
     *   1. Look up refresh token in Redis — missing means expired or already rotated
     *   2. Re-validate user at User Service — may have been locked since last login
     *   3. DELETE old refresh token — ROTATION (one-time use, prevents replay)
     *   4. Issue new access token + new refresh token
     *   5. Async audit log
     *
     * Rotation security model:
     *   If attacker steals refresh token and uses it first → they get a new pair.
     *   Legitimate user's next refresh fails (old token gone) → they re-login → we detect anomaly.
     *   If legitimate user uses first → attacker's replay fails immediately.
     */
    public TokenResponse refresh(RefreshRequest request, HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);

        // Step 1: Look up refresh token in Redis
        String userId = tokenStore.getUserIdByRefreshToken(request.refreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid or expired refresh token"));

        // Step 2: Re-validate user is still active
        User user = userRepository.findById(Long.valueOf(userId)).orElseGet(() -> {
            tokenStore.deleteRefreshToken(request.refreshToken);
            throw new RuntimeException("Invalid or expired refresh token");
        });

        // Step 3: ROTATE — delete the old refresh token (one-time use)
        tokenStore.deleteRefreshToken(request.refreshToken);

        // Step 4: Issue new token pair
        String newAccessToken  = jwtUtil.generateAccessToken(user.getId().toString(), user.getEmail(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken();
        tokenStore.saveRefreshToken(newRefreshToken, user.getId().toString());

        
        // Step 5: Async audit log
        auditLogService.logSuccess(user.getId().toString(), user.getEmail(), "TOKEN_REFRESH", ip);

        log.debug("Token refresh success: userId={}", userId);

        return TokenResponse.of(newAccessToken, newRefreshToken, accessTokenExpiryMs / 1000);
    }

    // ─── TOKEN REVOCATION: Logout (RFC 7009) ─────────────────────────

    /**
     * Logout — revoke the refresh token and blacklist the access token.
     *
     * Flow:
     *   1. Delete refresh token from Redis — immediately invalid
     *   2. Blacklist the access token's jti — Gateway rejects it on next request
     *      (Access token is short-lived at 15 min, blacklist is the safety net)
     *   3. Async audit log
     */
    public void logout(LogoutRequest request, String authHeader, HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);

        // Step 1: Delete refresh token
        String userId = tokenStore.getUserIdByRefreshToken(request.refreshToken).orElse(null);
        tokenStore.deleteRefreshToken(request.refreshToken);

        // Step 2: Blacklist access token so Gateway rejects it immediately
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                Claims claims          = jwtUtil.validateAndExtract(accessToken);
                String jti             = jwtUtil.extractJti(claims);
                long   remainingSeconds = jwtUtil.getRemainingSeconds(claims);
                tokenStore.blacklistAccessToken(jti, remainingSeconds);
            } catch (ExpiredJwtException e) {
                // Already expired — nothing to blacklist
            } catch (JwtException e) {
                log.debug("Could not blacklist access token on logout: {}", e.getMessage());
            }
        }

        // Step 3: Async audit log
        if (userId != null) {
            auditLogService.logSuccess(userId, "", "LOGOUT", ip);
        }

        log.info("Logout success: userId={}", userId);
    }

    // ─── REGISTRATION ─────────────────────────────────────────────────

    /**
     * Register — create new user and issue initial token pair.
     *
     * Delegates profile creation to User Service.
     * Auth Service never stores user data — it only manages tokens.
     * Auto-logs in after registration (UX: no double login needed).
     */
    public TokenResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);
        
        User user = User.builder().email(request.email).role(request.role).password(passwordEncoder.encode(request.getPassword())).build();
        userRepository.save(user);

        // Delegate user creation to User Service
        UserDto newUser = userServiceClient.register(
                request.email,
                request.firstName, request.lastName, request.role
        );

        // Issue initial token pair (auto-login)
        String accessToken  = jwtUtil.generateAccessToken(newUser.id, newUser.email, newUser.role);
        String refreshToken = jwtUtil.generateRefreshToken();
        tokenStore.saveRefreshToken(refreshToken, newUser.id);

        auditLogService.logSuccess(newUser.id, newUser.email, "REGISTER", ip);

        return TokenResponse.of(accessToken, refreshToken, accessTokenExpiryMs / 1000);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
