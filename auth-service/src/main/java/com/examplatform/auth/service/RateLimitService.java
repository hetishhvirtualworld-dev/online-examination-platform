package com.examplatform.auth.service;

import com.examplatform.auth.exception.AuthExceptions.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed rate limiting using a sliding window counter.
 *
 * WHY RATE LIMIT BY EMAIL (not IP)?
 *   Attackers rotate IP addresses trivially.
 *   The target is the email credential — rate limiting per email directly
 *   protects the account being attacked, even from distributed IPs.
 *
 * ALGORITHM: Sliding window counter via Redis INCR + TTL
 *   1. First attempt: SET key "1" with TTL = windowSeconds
 *   2. Subsequent:    INCR key (atomic — no race conditions)
 *   3. Over limit:    throw RateLimitExceededException
 *   4. Window resets: automatically when Redis key expires
 *   5. On success:    DELETE key (don't penalize successful logins)
 *
 * Key: rate_limit:login:{email}  ->  attempt count
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${auth.rate-limit.login.max-attempts:5}")
    private int maxLoginAttempts;

    @Value("${auth.rate-limit.login.window-seconds:900}")
    private long loginWindowSeconds;

    private static final String LOGIN_PREFIX   = "rate_limit:login:";
    private static final String REFRESH_PREFIX = "rate_limit:refresh:";

    /**
     * Check and increment login attempt counter for this email.
     *
     * Must be called BEFORE any credential validation to protect against
     * timing attacks that would reveal whether an email exists.
     *
     * @throws RateLimitExceededException if limit exceeded in the window
     */
    public void checkLoginRateLimit(String email) {
        checkAndIncrement(
                LOGIN_PREFIX + email.toLowerCase().trim(),
                maxLoginAttempts,
                loginWindowSeconds,
                "Too many login attempts. Please try again in 15 minutes."
        );
    }

    /**
     * Reset counter on successful login.
     * Prevents legitimate users from hitting limits due to past typos.
     */
    public void resetLoginAttempts(String email) {
        redisTemplate.delete(LOGIN_PREFIX + email.toLowerCase().trim());
    }

    /**
     * Rate limit token refresh — prevent refresh token grinding.
     * 20 refreshes per minute per user is generous for normal use.
     */
    public void checkRefreshRateLimit(String userId) {
        checkAndIncrement(
                REFRESH_PREFIX + userId,
                20, 60,
                "Too many token refresh attempts. Please try again shortly."
        );
    }

    // ------------------------------------------------------------------

    private void checkAndIncrement(String key, int maxAttempts, long windowSeconds, String message) {
        // INCR is atomic — no race conditions in distributed environment
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            // Redis failure — fail OPEN (don't block legitimate users due to Redis issue)
            log.error("Redis INCR returned null for key: {} — failing open", key);
            return;
        }

        // First attempt: set TTL to start the sliding window
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (count > maxAttempts) {
            log.warn("Rate limit exceeded: key={}, count={}", key, count);
            throw new RateLimitExceededException(message);
        }
    }
}
