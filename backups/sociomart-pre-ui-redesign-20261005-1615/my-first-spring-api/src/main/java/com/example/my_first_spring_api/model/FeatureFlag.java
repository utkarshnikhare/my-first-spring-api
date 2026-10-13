package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Platform-wide, Super-Admin-controlled configuration of a seller feature.
 *
 * A feature is:
 *   enabled/disabled platform-wide  ->  free or paid  ->  who may access it
 *   (via SellerFeatureGrant)        ->  optional limit
 *
 * Features are referenced by their string key everywhere in the code, so new
 * paid features can be introduced later without schema or code restructuring.
 */
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable identifier, e.g. "preorders", "menu_advance_days". */
    @Column(nullable = false, unique = true)
    private String featureKey;

    @Column(nullable = false)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Master switch: feature available on the platform at all. */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** FREE or PAID — paid features require an explicit grant per seller. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeaturePricingType pricingType = FeaturePricingType.FREE;

    /** Optional default limit (meaning depends on the feature, e.g. number of days). */
    @Column(name = "limit_value")
    private Integer limitValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public FeatureFlag() {}

    public FeatureFlag(String featureKey, String displayName, String description,
                       Boolean enabled, FeaturePricingType pricingType, Integer limitValue) {
        this.featureKey = featureKey;
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
        this.pricingType = pricingType;
        this.limitValue = limitValue;
    }

    public Long getId() { return id; }
    public String getFeatureKey() { return featureKey; }
    public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public FeaturePricingType getPricingType() { return pricingType; }
    public void setPricingType(FeaturePricingType pricingType) { this.pricingType = pricingType; }
    public Integer getLimitValue() { return limitValue; }
    public void setLimitValue(Integer limitValue) { this.limitValue = limitValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
