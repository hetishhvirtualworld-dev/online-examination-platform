package com.examplatform.auth.util;

import com.examplatform.auth.config.JwtProperties;
import com.examplatform.auth.dto.AuthDtos.UserDto;
import com.examplatform.auth.exception.AuthExceptions.TokenException;
import com.examplatform.auth.exception.AuthExceptions.TokenException.TokenErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token Provider — centralized token issuance and validation.
 *
 * ====== SECURITY DESIGN ======
 *
 * Algorithm: HMAC-SHA256 (hardcoded — never negotiated with client)
 *   WHY NOT RSA? Auth Service and API Gateway are in the same cluster,
 *   both controlled by us. HMAC-SHA256 is correct for same-domain trust.
 *   Use RSA/ECDSA only when EXTERNAL parties need to verify your tokens.
 *
 * jti claim: UUID per token — enables replay attack prevention.
 *   On logout, the jti is added to Redis blocklist with TTL = remaining
 *   token lifetime. Gateway checks blocklist before accepting any token.
 *
 * Algorithm confusion prevention: jjwt enforces the algorithm via the
 *   Key type. If an attacker sends alg:none or alg:RS256, the parser
 *   rejects it because our HMAC key type doesn't match RSA expectations.
 *
 * Clock skew: setAllowedClockSkewSeconds(30) handles pod clock drift
 *   in Kubernetes without allowing meaningful replay windows.
 *
 * Refresh tokens: Generated as opaque UUID (NOT JWT). See generateRefreshToken().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8)
        );
    }

    // ==============================================================
    //  TOKEN GENERATION
    // ==============================================================

    /**
     * Generates a signed JWT access token.
     *
     * Claims:
     *   sub       — userId (standard JWT subject claim)
     *   userId    — explicit, forwarded by Gateway as X-User-Id header
     *   email     — for downstream logging/notifications
     *   role      — Gateway enforces RBAC from this claim
     *   firstName — convenience for frontend
     *   iat, exp  — standard timing claims
     *   jti       — unique per token, enables blocklist/replay prevention
     */
    public String generateAccessToken(UserDto user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.accessToken().expiryMs());

        return Jwts.builder()
                // Standard claims
                .setSubject(user.id())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setId(UUID.randomUUID().toString())      // jti

                // Custom claims consumed by Gateway and downstream services
                .claim("userId",    user.id())
                .claim("email",     user.email())
                .claim("role",      user.role().name())
                .claim("firstName", user.firstName())

                // Algorithm hardcoded — never negotiate with client
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates an opaque refresh token.
     *
     * IMPORTANT — this is NOT a JWT. It is a random UUID.
     *
     * WHY UUID and NOT JWT?
     *   JWT = self-contained = unrevokable until expiry.
     *   If we used JWT as a refresh token, logout would be broken —
     *   the client could keep refreshing for 7 days even after logout.
     *
     *   UUID stored in Redis = server controls validity.
     *   Delete the Redis key = token is immediately invalid everywhere.
     *
     * This UUID is stored in Redis with a TTL and linked to the userId.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    // ==============================================================
    //  TOKEN VALIDATION
    // ==============================================================

    /**
     * Validates and parses an access token.
     *
     * jjwt validation sequence:
     *   1. Structural check — 3 segments, valid Base64URL
     *   2. Algorithm check  — must match HS256 (enforced by key type)
     *   3. Signature check  — HMAC-SHA256 verify with our secret
     *   4. Expiry check     — exp claim vs current time (with 30s skew)
     *   5. Not-before check — nbf claim if present
     *
     * Each failure throws a distinct exception for logging granularity.
     * Clients always receive a generic 401 — never the specific reason.
     *
     * @throws TokenException with specific error code for each failure
     */
    public Claims validateAndExtract(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(30)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: jti={}", safeGetJti(e));
            throw new TokenException(TokenErrorCode.EXPIRED, "Token has expired");

        } catch (SignatureException e) {
            // WARN not DEBUG — this may indicate tampering
            log.warn("JWT signature validation failed — possible tampering attempt");
            throw new TokenException(TokenErrorCode.INVALID_SIGNATURE, "Invalid token signature");

        } catch (MalformedJwtException e) {
            log.debug("Malformed JWT received");
            throw new TokenException(TokenErrorCode.MALFORMED, "Malformed token");

        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT type received — possible algorithm confusion attack");
            throw new TokenException(TokenErrorCode.UNSUPPORTED, "Unsupported token type");

        } catch (IllegalArgumentException e) {
            log.debug("Empty or null JWT token");
            throw new TokenException(TokenErrorCode.MISSING, "Token is empty");
        }
    }

    // ==============================================================
    //  CLAIM EXTRACTORS
    // ==============================================================

    public String extractUserId(Claims claims)    { return claims.get("userId",    String.class); }
    public String extractEmail(Claims claims)     { return claims.get("email",     String.class); }
    public String extractRole(Claims claims)      { return claims.get("role",      String.class); }
    public String extractJti(Claims claims)       { return claims.getId(); }
    public long   extractExpiryEpoch(Claims claims) { return claims.getExpiration().getTime() / 1000; }

    public boolean isValid(String token) {
        try { validateAndExtract(token); return true; }
        catch (TokenException e) { return false; }
    }

    // ------------------------------------------------------------------

    private String safeGetJti(ExpiredJwtException e) {
        try { return e.getClaims().getId(); } catch (Exception ex) { return "unknown"; }
    }
}
