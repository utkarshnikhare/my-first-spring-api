package com.example.my_first_spring_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Cluster lock table mapping for distributed scheduling coordination.
 */
@Entity
@Table(name = "cluster_locks")
public class ClusterLock {

    @Id
    @Column(name = "lock_key", length = 64, nullable = false)
    private String lockKey;

    @Column(name = "locked_until", nullable = false)
    private Instant lockedUntil;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    @Column(name = "locked_by", length = 255, nullable = false)
    private String lockedBy;

    public ClusterLock() {}

    public ClusterLock(String lockKey, Instant lockedUntil, Instant lockedAt, String lockedBy) {
        this.lockKey = lockKey;
        this.lockedUntil = lockedUntil;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
    }

    public String getLockKey() { return lockKey; }
    public void setLockKey(String lockKey) { this.lockKey = lockKey; }

    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }

    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
}
