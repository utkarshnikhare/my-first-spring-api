package com.example.my_first_spring_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Cluster rate limit bucket mapping for distributed persistent throttling.
 */
@Entity
@Table(name = "auth_rate_limit_buckets")
public class AuthRateLimitBucket {

    @Id
    @Column(name = "rate_key", length = 128, nullable = false)
    private String rateKey;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public AuthRateLimitBucket() {}

    public AuthRateLimitBucket(String rateKey, int hitCount, Instant windowStart, Instant lockedUntil) {
        this.rateKey = rateKey;
        this.hitCount = hitCount;
        this.windowStart = windowStart;
        this.lockedUntil = lockedUntil;
    }

    public String getRateKey() { return rateKey; }
    public void setRateKey(String rateKey) { this.rateKey = rateKey; }

    public int getHitCount() { return hitCount; }
    public void setHitCount(int hitCount) { this.hitCount = hitCount; }

    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }

    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
}
