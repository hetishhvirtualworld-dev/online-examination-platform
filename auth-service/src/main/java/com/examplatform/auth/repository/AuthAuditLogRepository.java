package com.examplatform.auth.repository;

import com.examplatform.auth.entity.AuthAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {

    List<AuthAuditLog> findByUserIdOrderByCreatedAtDesc(String userId);

    long countByEmailAndEventTypeAndCreatedAtAfter(
            String email,
            String eventType,
            Instant since
    );

    /**
     * Scheduled purge — removes logs older than retention period.
     */
    @Modifying
    @Query("DELETE FROM AuthAuditLog a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
