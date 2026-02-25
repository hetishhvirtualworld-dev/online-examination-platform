package com.examplatform.auth.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;

/**
 * Refresh Token entity stored in Redis.
 *
 * WHY REDIS (not PostgreSQL)?
 * - Automatic TTL expiry — no cron cleanup needed
 * - O(1) lookup by token value
 * - Fast enough for every refresh request
 *
 * WHY OPAQUE UUID (not JWT)?
 * - Fully revocable at any time (just delete the Redis key)
 * - Server-controlled validity — client cannot decode it
 * - If attacker steals it: single delete makes it useless immediately
 * - JWT as refresh token = self-contained = UNREVOKABLE until expiry
 *
 * Redis key pattern: refresh_token:{uuid-token}
 * Secondary index:   refresh_token:userId:{userId} -> set of token keys
 *                    (enables findAllByUserId for logout-all-devices)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@RedisHash("refresh_token")
public class RefreshTokenEntity implements Serializable {

    /**
     * The opaque UUID — this is what the client holds.
     * Acts as the Redis key suffix.
     */
    @Id
    private String token;

    @Indexed
    private String userId;

    private String email;
    private String role;

    private Instant expiresAt;

    /**
     * Redis TTL in seconds. Spring Data Redis uses this for automatic key expiry.
     */
    @TimeToLive
    private Long ttl;

    private Instant createdAt;

    private String deviceInfo;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
