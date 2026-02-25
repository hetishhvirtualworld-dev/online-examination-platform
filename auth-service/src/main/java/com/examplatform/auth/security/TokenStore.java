package com.examplatform.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * TokenStore — manages refresh tokens and access token blocklist in Redis.
 *
 * Refresh tokens:
 *   Key:   refresh:{token-uuid} → userId
 *   TTL:   7 days (configurable)
 *   Usage: login (save), refresh (lookup → rotate), logout (delete)
 *
 * Access token blocklist:
 *   Key:   token_blocklist:{jti} → "revoked"
 *   TTL:   remaining lifetime of the access token
 *   Usage: logout (write here), Gateway reads this same key to reject token
 *
 * Rate limit counters:
 *   Key:   rate_limit:{email} → attempt count
 *   TTL:   15 minutes (configurable)
 *   Usage: prevent brute force on login
 */
@Component
@Slf4j
public class TokenStore {

    private final StringRedisTemplate redis;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    @Value("${auth.rate-limit.max-attempts}")
    private int maxAttempts;

    @Value("${auth.rate-limit.window-seconds}")
    private long windowSeconds;

    public TokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ─── REFRESH TOKENS ───────────────────────────────────────────────

    public void saveRefreshToken(String token, String userId) {
        long ttlSeconds = refreshTokenExpiryMs / 1000;
        redis.opsForValue().set("refresh:" + token, userId, Duration.ofSeconds(ttlSeconds));
    }

    public Optional<String> getUserIdByRefreshToken(String token) {
        String userId = redis.opsForValue().get("refresh:" + token);
        return Optional.ofNullable(userId);
    }

    public void deleteRefreshToken(String token) {
        redis.delete("refresh:" + token);
    }

    // ─── ACCESS TOKEN BLOCKLIST ───────────────────────────────────────

    /**
     * Blacklist an access token's jti on logout.
     *
     * Gateway reads "token_blocklist:{jti}" on every request.
     * If this key exists → Gateway rejects the token with 401.
     * TTL = remaining token lifetime — Redis auto-cleans when token would expire anyway.
     */
    public void blacklistAccessToken(String jti, long remainingSeconds) {
        if (remainingSeconds <= 0) return;
        redis.opsForValue().set(
                "token_blocklist:" + jti,
                "revoked",
                Duration.ofSeconds(remainingSeconds)
        );
        log.debug("Access token blacklisted: jti={}", jti);
    }

    // ─── RATE LIMITING ────────────────────────────────────────────────

    /**
     * Increment login attempt counter for this email.
     * Returns true if limit is exceeded.
     */
    public boolean isRateLimited(String email) {
        String key  = "rate_limit:" + email.toLowerCase();
        Long   count = redis.opsForValue().increment(key);
        if (count == null) return false;

        if (count == 1) {
            // First attempt — start the window
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }

        return count > maxAttempts;
    }

    public void resetRateLimit(String email) {
        redis.delete("rate_limit:" + email.toLowerCase());
    }
}
