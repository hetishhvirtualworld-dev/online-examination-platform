package com.examplatform.auth.repository;

import com.examplatform.auth.entity.RefreshTokenEntity;
import com.examplatform.auth.entity.User;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data Redis Repository for refresh token CRUD.
 *
 * Redis key:           refresh_token:{token-uuid}
 * Secondary index:     refresh_token:userId:{userId} -> set of token keys
 *
 * The @Indexed annotation on userId in RefreshTokenEntity enables
 * findAllByUserId() — used for "logout from all devices".
 *
 * TTL is managed automatically by Redis via @TimeToLive. No cleanup needed.
 */
@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshTokenEntity, String> {

    Optional<RefreshTokenEntity> findById(String token);

    /**
     * All active sessions for a user.
     * Requires @Indexed on userId in RefreshTokenEntity.
     * Used for: logout-all-devices, show active sessions.
     */
    List<RefreshTokenEntity> findAllByUserId(String userId);

    /**
     * Count active sessions — enforces max concurrent session limit.
     */
    long countByUserId(String userId);
    
    void deleteByUserId(String userId);
}
