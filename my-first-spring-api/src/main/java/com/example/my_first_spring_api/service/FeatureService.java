package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.FeatureFlag;
import com.example.my_first_spring_api.model.FeaturePricingType;
import com.example.my_first_spring_api.model.SellerFeatureGrant;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.FeatureFlagRepository;
import com.example.my_first_spring_api.repository.SellerFeatureGrantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central, configuration-driven feature/permission system.
 *
 * Feature -> enabled/disabled -> free/paid -> who can access -> optional limit
 *
 * Nothing about a feature is hard-coded at call sites: code only asks
 * "may this seller use feature X?" / "what is the seller's limit for X?".
 * New features are added by inserting a FeatureFlag row (seeded in
 * {@link #ensureDefaults()}) — no structural changes required later.
 */
@Service
public class FeatureService {

    // ---- Known feature keys (the platform's first configurable features) ----
    public static final String KEY_PREORDERS = "preorders";
    public static final String KEY_MENU_ADVANCE_DAYS = "menu_advance_days";

    private final FeatureFlagRepository featureFlagRepository;
    private final SellerFeatureGrantRepository grantRepository;

    @Autowired
    public FeatureService(FeatureFlagRepository featureFlagRepository,
                          SellerFeatureGrantRepository grantRepository) {
        this.featureFlagRepository = featureFlagRepository;
        this.grantRepository = grantRepository;
    }

    /** Seeds the initial feature catalogue once. Called at startup. */
    @Transactional
    public void ensureDefaults() {
        upsertSeed(KEY_PREORDERS,
                "Accept future / pre-orders",
                "Allow a seller to take orders scheduled for future dates.",
                true, FeaturePricingType.FREE, null);
        upsertSeed(KEY_MENU_ADVANCE_DAYS,
                "Publish menus in advance",
                "How many days ahead a seller can publish menus (today + tomorrow are the free baseline).",
                true, FeaturePricingType.FREE, 2);
    }

    private void upsertSeed(String key, String name, String description,
                            boolean enabled, FeaturePricingType pricing, Integer limit) {
        if (!featureFlagRepository.existsByFeatureKey(key)) {
            featureFlagRepository.save(new FeatureFlag(key, name, description, enabled, pricing, limit));
        }
    }

    /** Super Admin: create or update a feature configuration. */
    @Transactional
    public FeatureFlag upsertFlag(String featureKey, String displayName, String description,
                                  Boolean enabled, FeaturePricingType pricingType, Integer limitValue) {
        FeatureFlag flag = featureFlagRepository.findByFeatureKey(featureKey)
                .orElseGet(() -> {
                    FeatureFlag f = new FeatureFlag();
                    f.setFeatureKey(featureKey);
                    return f;
                });
        if (displayName != null) flag.setDisplayName(displayName);
        if (description != null) flag.setDescription(description);
        if (enabled != null) flag.setEnabled(enabled);
        if (pricingType != null) flag.setPricingType(pricingType);
        if (limitValue != null) flag.setLimitValue(limitValue);
        return featureFlagRepository.save(flag);
    }

    /** Is the feature switched on at platform level at all? */
    @Transactional(readOnly = true)
    public boolean isPlatformEnabled(String featureKey) {
        return featureFlagRepository.findByFeatureKey(featureKey)
                .map(f -> Boolean.TRUE.equals(f.getEnabled()))
                .orElse(false);
    }

    /**
     * May this seller use the feature?
     * disabled platform-wide        -> no
     * FREE                          -> yes
     * PAID                          -> only with an explicit granted=true grant
     * explicit granted=false grant  -> no (seller blocked even on FREE features)
     */
    @Transactional(readOnly = true)
    public boolean sellerHasAccess(User seller, String featureKey) {
        FeatureFlag flag = featureFlagRepository.findByFeatureKey(featureKey).orElse(null);
        if (flag == null || !Boolean.TRUE.equals(flag.getEnabled())) return false;
        SellerFeatureGrant grant = grantRepository
                .findBySellerIdAndFeatureKey(seller.getId(), featureKey).orElse(null);
        if (grant != null && grant.getGranted() != null) return grant.getGranted();
        return flag.getPricingType() == FeaturePricingType.FREE;
    }

    /** Effective limit for a seller (grant override wins over platform default). */
    @Transactional(readOnly = true)
    public FeatureFlag getFlag(String featureKey) {
        return featureFlagRepository.findByFeatureKey(featureKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown feature: " + featureKey));
    }

    /** Effective limit for a seller (grant override wins over platform default). */
    @Transactional(readOnly = true)
    public Integer sellerLimit(User seller, String featureKey) {
        FeatureFlag flag = featureFlagRepository.findByFeatureKey(featureKey).orElse(null);
        Integer platformDefault = flag != null ? flag.getLimitValue() : null;
        return grantRepository.findBySellerIdAndFeatureKey(seller.getId(), featureKey)
                .map(SellerFeatureGrant::getLimitOverride)
                .orElse(platformDefault);
    }

    /** Guard for seller actions — fails with a clear, user-facing message. */
    @Transactional(readOnly = true)
    public void assertSellerCanUse(User seller, String featureKey) {
        if (!sellerHasAccess(seller, featureKey)) {
            FeatureFlag flag = featureFlagRepository.findByFeatureKey(featureKey).orElse(null);
            String name = flag != null ? flag.getDisplayName() : featureKey;
            boolean paid = flag != null && flag.getPricingType() == FeaturePricingType.PAID;
            throw new IllegalStateException("The '" + name + "' feature is not available for your kitchen"
                    + (paid ? " (paid feature — requires platform approval)." : " (currently disabled)."));
        }
    }

    /** Super Admin: grant or block one feature for one seller. */
    @Transactional
    public SellerFeatureGrant setSellerGrant(Long sellerId, String featureKey, boolean granted, Integer limitOverride) {
        getFlag(featureKey); // validate the feature exists
        SellerFeatureGrant grant = grantRepository.findBySellerIdAndFeatureKey(sellerId, featureKey)
                .orElseGet(() -> new SellerFeatureGrant(sellerId, featureKey, granted, limitOverride));
        grant.setGranted(granted);
        grant.setLimitOverride(limitOverride);
        return grantRepository.save(grant);
    }

    @Transactional(readOnly = true)
    public List<SellerFeatureGrant> getGrantsForSeller(Long sellerId) {
        return grantRepository.findBySellerId(sellerId);
    }

    @Transactional(readOnly = true)
    public List<FeatureFlag> getAllFlags() {
        return featureFlagRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Map<String, FeatureFlag> flagsByKey() {
        return featureFlagRepository.findAll().stream()
                .collect(Collectors.toMap(FeatureFlag::getFeatureKey, Function.identity()));
    }
}
