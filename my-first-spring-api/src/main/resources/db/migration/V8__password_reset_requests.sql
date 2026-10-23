-- V8__password_reset_requests.sql
-- Manual (Admin-mediated) password recovery V1. A seller or admin requests a reset
-- by mobile number, a Super Admin reviews the request and issues a one-time
-- temporary password (BCrypt-hashed here, plaintext shown to the reviewer ONCE),
-- and the user is forced to change it at first login.
--
-- status: PENDING -> APPROVED (temp password generated) | REJECTED (closed)
-- The temp password expires 24 hours after generation or on first use, whichever
-- comes first (temp_password_used + temp_password_expires_at).
CREATE TABLE IF NOT EXISTS password_reset_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT,
    temp_password_hash VARCHAR(100),
    temp_password_used BOOLEAN NOT NULL DEFAULT FALSE,
    temp_password_expires_at TIMESTAMP WITHOUT TIME ZONE,
    action_time TIMESTAMP WITHOUT TIME ZONE,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_prr_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_prr_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id)
);

ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'PENDING';
ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS reviewer_id BIGINT;
ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS temp_password_hash VARCHAR(100);
ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS temp_password_used BOOLEAN DEFAULT FALSE;
ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS temp_password_expires_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS action_time TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);
ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_prr_user_id ON password_reset_requests (user_id);
CREATE INDEX IF NOT EXISTS idx_prr_status ON password_reset_requests (status);
