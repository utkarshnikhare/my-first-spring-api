-- V6__password_reset_tokens.sql
-- Single-use, time-limited password reset tokens (part of the forget-password flow).
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

ALTER TABLE password_reset_tokens ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE password_reset_tokens ADD COLUMN IF NOT EXISTS token VARCHAR(64);
ALTER TABLE password_reset_tokens ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE password_reset_tokens ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE password_reset_tokens ADD COLUMN IF NOT EXISTS used BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_prt_user_id ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_prt_token ON password_reset_tokens (token);
