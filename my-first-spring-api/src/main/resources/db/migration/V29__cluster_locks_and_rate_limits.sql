-- V29__cluster_locks_and_rate_limits.sql
-- Enables distributed locking for scheduled background jobs and persistent cluster-safe auth rate limiting.

CREATE TABLE IF NOT EXISTS cluster_locks (
    lock_key VARCHAR(64) PRIMARY KEY,
    locked_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_rate_limit_buckets (
    rate_key VARCHAR(128) PRIMARY KEY,
    hit_count INT NOT NULL DEFAULT 0,
    window_start TIMESTAMP NOT NULL,
    locked_until TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cluster_locks_until ON cluster_locks(locked_until);
CREATE INDEX IF NOT EXISTS idx_rate_limit_locked ON auth_rate_limit_buckets(locked_until);
