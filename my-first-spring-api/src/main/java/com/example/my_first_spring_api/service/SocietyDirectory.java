package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Read-only directory of the societies that already exist on the platform.
 *
 * <p>V1 service areas are references to existing societies only (no GPS, maps,
 * radius or geofencing). The directory is derived from existing user and kitchen
 * data — it never creates or stores duplicate society records.</p>
 *
 * <p>Used by the seller (Manage Kitchen) and admin service-area screens so that
 * both sides select from the same known list, and by the backend save paths so
 * that service-area updates are validated authoritatively on the server.</p>
 */
@Service
public class SocietyDirectory {

    private final UserRepository userRepository;
    private final KitchenRepository kitchenRepository;

    @Autowired
    public SocietyDirectory(UserRepository userRepository, KitchenRepository kitchenRepository) {
        this.userRepository = userRepository;
        this.kitchenRepository = kitchenRepository;
    }

    /**
     * All existing societies, deduplicated case-insensitively and sorted.
     */
    @Transactional(readOnly = true)
    public List<String> findAllSocieties() {
        Map<String, String> known = knownSocieties();
        List<String> result = new ArrayList<>(known.values());
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    /**
     * Validates a comma-separated service-area selection against the existing
     * societies and returns the normalized value to persist:
     * <ul>
     *   <li>unknown societies are rejected with {@link IllegalArgumentException};</li>
     *   <li>duplicates (including case variants) are collapsed safely and the
     *       result is stored in deterministic (case-insensitive sorted) order;</li>
     *   <li>entries are canonicalized to the stored society spelling;</li>
     *   <li>blank input clears the selection (the existing empty-service-area
     *       semantics — fall back to the kitchen's primary society — apply).</li>
     * </ul>
     */
    public String validateAndNormalize(String serviceAreas) {
        if (serviceAreas == null || serviceAreas.isBlank()) return "";

        Map<String, String> known = knownSocieties();
        Set<String> selected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> unknown = new ArrayList<>();
        for (String part : serviceAreas.split(",")) {
            String candidate = part.trim();
            if (candidate.isEmpty()) continue;
            String canonical = known.get(candidate);
            if (canonical == null) {
                if (!unknown.stream().anyMatch(u -> u.equalsIgnoreCase(candidate))) unknown.add(candidate);
                continue;
            }
            selected.add(canonical); // TreeSet de-duplicates case variants safely
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown societies: " + String.join(", ", unknown)
                    + ". Please select from the existing societies.");
        }
        return String.join(",", selected);
    }

    /**
     * Existing societies sourced from: every user's profile society, every
     * kitchen's primary society, and any society already referenced by a
     * stored service-area selection (so existing configurations stay editable).
     */
    private Map<String, String> knownSocieties() {
        Map<String, String> known = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (User user : userRepository.findAll()) {
            addSociety(known, user == null ? null : user.getSociety());
        }
        for (Kitchen kitchen : kitchenRepository.findAll()) {
            if (kitchen == null) continue;
            addSociety(known, kitchen.getSociety());
            addServiceAreaEntries(known, kitchen.getServiceAreas());
        }
        return known;
    }

    private void addServiceAreaEntries(Map<String, String> known, String serviceAreas) {
        if (serviceAreas == null || serviceAreas.isBlank()) return;
        for (String part : serviceAreas.split(",")) {
            addSociety(known, part);
        }
    }

    private void addSociety(Map<String, String> known, String society) {
        if (society == null) return;
        String trimmed = society.trim();
        if (trimmed.isEmpty()) return;
        known.putIfAbsent(trimmed, trimmed);
    }
}