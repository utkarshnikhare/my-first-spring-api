package com.example.my_first_spring_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Cluster-safe distributed locking mechanism backed by PostgreSQL / database.
 * Eliminates duplicate executions of @Scheduled cron jobs across multi-instance
 * horizontal scaling on Render without requiring external Redis or ShedLock infrastructure.
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final String instanceId = UUID.randomUUID().toString();

    @Autowired
    public DistributedLockService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    public DistributedLockService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /**
     * Attempts to acquire a distributed lock for the specified duration.
     * Returns true if lock was successfully acquired, false if held by another node.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryLock(String lockKey, Duration duration) {
        if (jdbcTemplate == null) {
            return true;
        }
        Instant now = Instant.now(clock);
        Instant lockUntil = now.plus(duration);
        Timestamp nowTs = Timestamp.from(now);
        Timestamp untilTs = Timestamp.from(lockUntil);

        try {
            // 1. Try updating an existing or expired lock atomically
            int updated = jdbcTemplate.update(
                    "UPDATE cluster_locks SET locked_until = ?, locked_at = ?, locked_by = ? " +
                    "WHERE lock_key = ? AND locked_until <= ?",
                    untilTs, nowTs, instanceId, lockKey, nowTs);
            if (updated > 0) {
                return true;
            }

            // 2. Try inserting a new lock row if not already present
            try {
                int inserted = jdbcTemplate.update(
                        "INSERT INTO cluster_locks (lock_key, locked_until, locked_at, locked_by) VALUES (?, ?, ?, ?)",
                        lockKey, untilTs, nowTs, instanceId);
                return inserted > 0;
            } catch (DuplicateKeyException dke) {
                // Another node inserted it concurrently
                return false;
            }
        } catch (Exception ex) {
            log.warn("Could not acquire distributed lock for {}: {}", lockKey, ex.getMessage());
            return false;
        }
    }

    /**
     * Releases the lock early when the execution completes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unlock(String lockKey) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE cluster_locks SET locked_until = locked_at WHERE lock_key = ? AND locked_by = ?",
                    lockKey, instanceId);
        } catch (Exception ex) {
            log.warn("Could not release distributed lock for {}: {}", lockKey, ex.getMessage());
        }
    }
}
