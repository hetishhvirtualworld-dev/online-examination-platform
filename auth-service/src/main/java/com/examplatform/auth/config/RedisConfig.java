package com.examplatform.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for:
 * 1. Refresh Token storage (via RefreshTokenRepository)
 * 2. Login attempt rate limiting (RateLimitService)
 * 3. Access token blocklist for immediate revocation (TokenBlacklistService)
 *
 * Using Lettuce (not Jedis) — reactive-compatible, better for Spring Boot 3.
 * Connection pool configured in application.yml.
 */
@Configuration
@EnableRedisRepositories(basePackages = "com.examplatform.auth.repository")
public class RedisConfig {

    /**
     * General-purpose RedisTemplate for rate limiting and blocklist.
     * RefreshToken entities use Spring Data Redis Repository (auto-configured).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
