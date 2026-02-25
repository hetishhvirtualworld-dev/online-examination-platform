package com.examplatform.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * AuthAuditLog — security event log in PostgreSQL.
 *
 * Every login success/failure, refresh, logout is recorded here.
 * Append-only — never updated. Purged after 90 days by scheduled job.
 * Stored in PostgreSQL, NOT Redis — audit logs must survive Redis restarts.
 */
@Entity
@Table(name = "auth_audit_log", indexes = {
        @Index(name = "idx_audit_email",      columnList = "email"),
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "event_type", nullable = false)
    private String eventType;   // LOGIN_SUCCESS, LOGIN_FAILURE, REFRESH, LOGOUT, REGISTER

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "success", nullable = false)
    private boolean success;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
