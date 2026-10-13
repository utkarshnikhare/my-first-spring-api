package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.model.FeaturePricingType;
import com.example.my_first_spring_api.model.SellerFeatureGrant;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.PlatformSettingRepository;
import com.example.my_first_spring_api.service.AdminService;
import com.example.my_first_spring_api.service.FeatureService;
import com.example.my_first_spring_api.model.PlatformSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SUPER_ADMIN API surface — the highest platform control level:
 * manage Admin accounts, configure features (enabled / free-paid / limits),
 * decide which sellers access paid features, and manage platform settings.
 * No Super Admin UI exists yet; these endpoints are the connection points
 * for the future Super Admin screens.
 */
@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminController {

    private final AdminService adminService;
    private final FeatureService featureService;
    private final PlatformSettingRepository platformSettingRepository;

    @Autowired
    public SuperAdminController(AdminService adminService, FeatureService featureService,
                                PlatformSettingRepository platformSettingRepository) {
        this.adminService = adminService;
        this.featureService = featureService;
        this.platformSettingRepository = platformSettingRepository;
    }

    // ---------------- Admin management ----------------

    @GetMapping("/admins")
    public ResponseEntity<List<Map<String, Object>>> admins() {
        return ResponseEntity.ok(adminService.listAdmins().stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("name", u.getName());
                    m.put("mobileNumber", u.getMobileNumber());
                    m.put("role", u.getRole());
                    return m;
                }).collect(Collectors.toList()));
    }

    @PostMapping("/admins")
    public ResponseEntity<Map<String, Object>> createAdmin(@RequestBody Map<String, String> body) {
        User admin = adminService.createAdmin(body.get("name"), body.get("mobileNumber"));
        return ResponseEntity.ok(Map.of(
                "id", admin.getId(),
                "name", admin.getName(),
                "mobileNumber", admin.getMobileNumber(),
                "role", admin.getRole().name()));
    }

    /** Demotes an ADMIN back to BUYER. Super Admin accounts can never be demoted. */
    @DeleteMapping("/admins/{id}")
    public ResponseEntity<Map<String, Object>> demoteAdmin(@PathVariable Long id) {
        User admin = adminService.demoteAdmin(id);
        return ResponseEntity.ok(Map.of("id", admin.getId(), "role", admin.getRole().name()));
    }

    // ---------------- Feature configuration (enabled / free-paid / limits) ----------------

    @GetMapping("/features")
    public ResponseEntity<List<Map<String, Object>>> features() {
        return ResponseEntity.ok(featureService.getAllFlags().stream()
                .map(SuperAdminController::toFeatureSummary).collect(Collectors.toList()));
    }

    @PutMapping("/features/{featureKey}")
    public ResponseEntity<Map<String, Object>> updateFeature(@PathVariable String featureKey,
                                                             @RequestBody Map<String, Object> body) {
        featureService.upsertFlag(featureKey,
                (String) body.get("displayName"),
                (String) body.get("description"),
                body.containsKey("enabled") ? Boolean.parseBoolean(String.valueOf(body.get("enabled"))) : null,
                body.containsKey("pricingType") ? FeaturePricingType.valueOf(String.valueOf(body.get("pricingType"))) : null,
                body.containsKey("limitValue") ? Integer.valueOf(String.valueOf(body.get("limitValue"))) : null);
        return ResponseEntity.ok(Map.of("message", "Feature configuration saved"));
    }

    /** Decide which seller can access a (paid) feature, with optional custom limit. */
    @PutMapping("/sellers/{sellerId}/features/{featureKey}")
    public ResponseEntity<Map<String, Object>> setSellerFeature(@PathVariable Long sellerId,
                                                                @PathVariable String featureKey,
                                                                @RequestBody Map<String, Object> body) {
        boolean granted = Boolean.parseBoolean(String.valueOf(body.getOrDefault("granted", "true")));
        Integer limitOverride = body.containsKey("limitOverride")
                ? Integer.valueOf(String.valueOf(body.get("limitOverride"))) : null;
        SellerFeatureGrant grant = featureService.setSellerGrant(sellerId, featureKey, granted, limitOverride);
        return ResponseEntity.ok(Map.of(
                "sellerId", grant.getSellerId(),
                "featureKey", grant.getFeatureKey(),
                "granted", grant.getGranted(),
                "limitOverride", grant.getLimitOverride() == null ? -1 : grant.getLimitOverride()));
    }

    @GetMapping("/sellers/{sellerId}/features")
    public ResponseEntity<List<SellerFeatureGrant>> sellerFeatures(@PathVariable Long sellerId) {
        return ResponseEntity.ok(featureService.getGrantsForSeller(sellerId));
    }

    static Map<String, Object> toFeatureSummary(com.example.my_first_spring_api.model.FeatureFlag f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("featureKey", f.getFeatureKey());
        m.put("displayName", f.getDisplayName());
        m.put("description", f.getDescription());
        m.put("enabled", f.getEnabled());
        m.put("pricingType", f.getPricingType());
        m.put("limitValue", f.getLimitValue());
        return m;
    }

    // ---------------- Platform settings ----------------

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> settings() {
        Map<String, String> out = new LinkedHashMap<>();
        for (PlatformSetting s : platformSettingRepository.findAll()) {
            out.put(s.getSettingKey(), s.getSettingValue());
        }
        return ResponseEntity.ok(out);
    }

    @PutMapping("/settings/{key}")
    public ResponseEntity<Map<String, Object>> setSetting(@PathVariable String key,
                                                          @RequestBody Map<String, String> body) {
        PlatformSetting setting = platformSettingRepository.findBySettingKey(key)
                .orElseGet(() -> new PlatformSetting(key, null));
        setting.setSettingValue(body.get("value"));
        platformSettingRepository.save(setting);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "value", setting.getSettingValue() == null ? "" : setting.getSettingValue()));
    }

    // ---------------- Platform analytics (highest level) ----------------

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ResponseEntity.ok(adminService.analyticsSummary());
    }
}

