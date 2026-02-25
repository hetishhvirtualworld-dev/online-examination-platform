-- Auth Service schema
-- Managed by Flyway — do not edit after first deployment

CREATE TABLE IF NOT EXISTS auth_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36),
    email VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    failure_reason VARCHAR(255),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE INDEX idx_audit_email ON auth_audit_log (email);
CREATE INDEX idx_audit_event_type ON auth_audit_log (event_type);
CREATE INDEX idx_audit_created_at ON auth_audit_log (created_at DESC);

ALTER TABLE auth_audit_log 
COMMENT = 'Security audit log — append-only, 90-day retention';