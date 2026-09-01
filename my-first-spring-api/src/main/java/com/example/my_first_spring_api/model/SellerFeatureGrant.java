package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Per-seller access decision for one feature. This is how the Super Admin
 * "decides which sellers can access specific paid features".
 *
 * granted=true  -> seller may use the feature (optionally with a custom limit)
 * granted=false -> seller is explicitly blocked, even if the feature is FREE
 */
@Entity
@Table(name = "seller_feature_grants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"seller_id", "feature_key"}))
public class SellerFeatureGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "feature_key", nullable = false)
    private String featureKey;

    @Column(nullable = false)
    private Boolean granted = false;

    /** Overrides the platform-wide limitValue for this seller when set. */
    @Column(name = "limit_override")
    private Integer limitOverride;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public SellerFeatureGrant() {}

    public SellerFeatureGrant(Long sellerId, String featureKey, Boolean granted, Integer limitOverride) {
        this.sellerId = sellerId;
        this.featureKey = featureKey;
        this.granted = granted;
        this.limitOverride = limitOverride;
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getFeatureKey() { return featureKey; }
    public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
    public Boolean getGranted() { return granted; }
    public void setGranted(Boolean granted) { this.granted = granted; }
    public Integer getLimitOverride() { return limitOverride; }
    public void setLimitOverride(Integer limitOverride) { this.limitOverride = limitOverride; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
