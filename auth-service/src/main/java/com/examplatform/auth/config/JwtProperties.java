package com.examplatform.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed JWT configuration bound from application.yml.
 *
 * Validated at startup — if jwt.secret is missing the app fails fast with a
 * clear error rather than a cryptic NPE at runtime.
 *
 * Never use @Value for security-critical config — this approach is auditable,
 * testable, and type-safe.
 */
@ConfigurationProperties(prefix = "jwt")
@Validated
public record JwtProperties(

		@NotBlank(message = "JWT secret must not be blank") 
		@Size(min = 32, message = "JWT secret must be at least 32 characters (256 bits)") 
		String secret,
		AccessToken accessToken, 
		RefreshToken refreshToken ) {
	
	public record AccessToken(
			@Min(value = 60_000, message = "Access token expiry must be at least 60 seconds") long expiryMs) {
		public long expirySeconds() {
			return expiryMs / 1000;
		}
	}

	public record RefreshToken(
			@Min(value = 3_600_000, message = "Refresh token expiry must be at least 1 hour") long expiryMs,
			int maxPerUser) {
		public long expirySeconds() {
			return expiryMs / 1000;
		}
	}
}
