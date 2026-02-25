package com.examplatform.auth.service;

import com.examplatform.auth.entity.AuthAuditLog;
import com.examplatform.auth.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * AuditLogService — writes security events to PostgreSQL asynchronously.
 *
 * @Async on all write methods — audit logging must NEVER slow down login.
 * If the database is slow, authentication still succeeds. Logs are best-effort.
 *
 * Scheduled purge runs weekly to delete logs older than 90 days.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void logSuccess(String userId, String email, String eventType, String ipAddress) {
        save(AuthAuditLog.builder()
                .userId(userId)
                .email(email)
                .eventType(eventType)
                .ipAddress(ipAddress)
                .success(true)
                .build());
    }

    @Async
    public void logFailure(String email, String eventType, String reason, String ipAddress) {
        save(AuthAuditLog.builder()
                .email(email)
                .eventType(eventType)
                .failureReason(reason)
                .ipAddress(ipAddress)
                .success(false)
                .build());
    }

    @Scheduled(cron = "0 0 2 * * SUN")
    @Transactional
    public void purgeOldLogs() {
        Instant cutoff  = Instant.now().minus(90, ChronoUnit.DAYS);
        int     deleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Audit log purge: {} records deleted", deleted);
    }

    private void save(AuthAuditLog authLog) {
        try {
            auditLogRepository.save(authLog);
        } catch (Exception e) {
            // Non-critical — log to app log, don't propagate
            log.warn("Failed to save audit log: {}", e.getMessage());
        }
    }
}
