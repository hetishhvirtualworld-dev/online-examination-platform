package com.examplatform.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * JwtUtil — token generation and validation.
 *
 * Mirrors the Gateway's JwtUtil exactly in structure and style.
 * Same secret, same algorithm (HS256), same claim names —
 * this is what makes Gateway validation work on tokens we issue.
 *
 * Gateway's JwtUtil:       validates only
 * Auth Service's JwtUtil:  generates + validates
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ─── GENERATION ──────────────────────────────────────────────────

    /**
     * Generates a signed JWT access token.
     *
     * Claim names match exactly what Gateway's JwtUtil reads:
     *   userId → forwarded as X-User-Id header
     *   role   → used for RBAC in RouterValidator
     *   email  → forwarded as X-User-Email header
     *
     * jti claim: unique per token, used by Gateway's blocklist check after logout.
     */
    public String generateAccessToken(String userId, String email, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setId(UUID.randomUUID().toString())
                .claim("userId", userId)
                .claim("email",  email)
                .claim("role",   role)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates an opaque refresh token — a random UUID, NOT a JWT.
     *
     * Why NOT a JWT for refresh?
     * JWT = self-contained = cannot be revoked until expiry.
     * Opaque UUID stored in Redis = delete the Redis key = instantly invalid.
     * If someone logs out, their refresh token is gone from Redis immediately.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    // ─── VALIDATION ──────────────────────────────────────────────────

    /**
     * Validates a JWT and returns its claims.
     * Identical logic to Gateway's JwtUtil.validateAndExtract().
     */
    public Claims validateAndExtract(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .setAllowedClockSkewSeconds(30)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // ─── EXTRACTORS — same method names as Gateway's JwtUtil ─────────

    public String extractUserId(Claims claims) { return claims.get("userId", String.class); }
    public String extractEmail(Claims claims)  { return claims.get("email",  String.class); }
    public String extractRole(Claims claims)   { return claims.get("role",   String.class); }
    public String extractJti(Claims claims)    { return claims.getId(); }

    public long getRemainingSeconds(Claims claims) {
        long exp = claims.getExpiration().getTime() / 1000;
        long now = System.currentTimeMillis() / 1000;
        return Math.max(0, exp - now);
    }
}
