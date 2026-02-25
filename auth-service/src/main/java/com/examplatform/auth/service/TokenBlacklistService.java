package com.examplatform.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Access token blocklist using Redis.
 *
 * THE PROBLEM: JWT access tokens are stateless — once issued, they're valid
 * until expiry. Logout cannot "delete" a JWT because the client holds it.
 *
 * THE SOLUTION: Maintain a server-side blocklist keyed by the token's jti claim.
 *   - On logout, add the token's jti to Redis with TTL = remaining token lifetime
 *   - API Gateway checks: is this jti in the blocklist? If yes, reject with 401
 *   - Redis auto-expires the key when the token would have expired anyway
 *   - Memory cost: one Redis key per logged-out (not-yet-expired) token
 *
 * WHY NOT ALWAYS BLOCKLIST?
 *   15-minute access tokens expire fast. In most cases, logout + short expiry
 *   is good enough without a blocklist. We blocklist on explicit logout for
 *   the critical security cases: account compromise, forced logout, role change.
 *
 * Redis key: token_blocklist:{jti}  ->  "revoked"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String BLOCKLIST_PREFIX = "token_blocklist:";

    /**
     * Add a token's jti to the blocklist.
     *
     * @param jti   JWT ID claim from the access token
     * @param ttlSeconds  seconds until the token would naturally expire
     */
    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;  // Already expired — nothing to blocklist
        }
        redisTemplate.opsForValue().set(
                BLOCKLIST_PREFIX + jti,
                "revoked",
                Duration.ofSeconds(ttlSeconds)
        );
        log.debug("Access token blacklisted: jti={}, ttl={}s", jti, ttlSeconds);
    }

    /**
     * Check if a jti is on the blocklist.
     * Called by API Gateway on every request (fast Redis GET).
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLOCKLIST_PREFIX + jti));
    }
}
