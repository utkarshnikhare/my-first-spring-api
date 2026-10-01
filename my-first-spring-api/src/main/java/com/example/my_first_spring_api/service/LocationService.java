package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.repository.AreaRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.SocietyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The ONE authoritative location and service-coverage service.
 *
 * <p>Every place on the platform that needs to know "where is this?" or "may this
 * seller serve this buyer?" goes through this service, so there is exactly one
 * implementation of each rule:</p>
 * <ul>
 *   <li>listing the active Areas;</li>
 *   <li>listing the active Societies of an Area;</li>
 *   <li>validating that a Society belongs to an Area and both are active;</li>
 *   <li>creating / renaming / enabling / disabling master records;</li>
 *   <li>reading and saving a seller's explicit Society coverage;</li>
 *   <li>answering "does this seller serve this Society?".</li>
 * </ul>
 *
 * <p>Kitchens and Homemade Products share it because both are stored as
 * {@link Kitchen} rows with {@code sellerType} - there is no second coverage
 * engine and no second location master.</p>
 *
 * <p>Validation rules enforced here (never only in the frontend):</p>
 * <ul>
 *   <li>names are trimmed and must be non-blank;</li>
 *   <li>society names are unique case-insensitively within their Area;</li>
 *   <li>the same society name MAY exist under a different Area;</li>
 *   <li>inactive Areas/Societies cannot be chosen for NEW records;</li>
 *   <li>master records are disabled, never deleted, so buyers, sellers and
 *       historical orders keep resolving.</li>
 * </ul>
 *
 * <p>Unresolved legacy free-text societies (a bare society string with no Area)
 * are deliberately NOT auto-assigned to an Area - that would be guessing. See
 * {@link #resolveLegacySociety}.</p>
 */
@Service
public class LocationService {

    private final AreaRepository areaRepository;
    private final SocietyRepository societyRepository;
    private final KitchenRepository kitchenRepository;

    @Autowired
    public LocationService(AreaRepository areaRepository, SocietyRepository societyRepository,
                           KitchenRepository kitchenRepository) {
        this.areaRepository = areaRepository;
        this.societyRepository = societyRepository;
        this.kitchenRepository = kitchenRepository;
    }

    // ==================== READS ====================

    /** Active Areas only - the list every NEW selection is built from. */
    @Transactional(readOnly = true)
    public List<Area> findActiveAreas() {
        return areaRepository.findByActiveTrueOrderByNameAsc();
    }

    /** All Areas including inactive ones, so the Admin screen can re-enable them. */
    @Transactional(readOnly = true)
    public List<Area> findAllAreas() {
        List<Area> areas = new ArrayList<>(areaRepository.findAll());
        areas.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return areas;
    }

    /** Active Societies of one Area, alphabetically. */
    @Transactional(readOnly = true)
    public List<Society> findActiveSocieties(Long areaId) {
        return societyRepository.findByAreaIdAndActiveTrueOrderByNameAsc(areaId);
    }

    /** Every Society of one Area including disabled, for the Admin screen. */
    @Transactional(readOnly = true)
    public List<Society> findAllSocieties(Long areaId) {
        List<Society> societies = new ArrayList<>(societyRepository.findByAreaIdOrderByNameAsc(areaId));
        societies.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return societies;
    }

    @Transactional(readOnly = true)
    public Optional<Area> findArea(Long areaId) {
        return areaRepository.findById(areaId);
    }

    @Transactional(readOnly = true)
    public Optional<Society> findSociety(Long societyId) {
        return societyRepository.findById(societyId);
    }

    // ==================== MASTER-DATA LIFECYCLE ====================

    @Transactional
    public Area createArea(String name) {
        String normalized = requireName(name, "Area");
        if (areaRepository.findByNameIgnoreCase(normalized).isPresent()) {
            throw new IllegalArgumentException("An area called \"" + normalized + "\" already exists.");
        }
        return areaRepository.save(new Area(normalized));
    }

    @Transactional
    public Area renameArea(Long areaId, String name) {
        Area area = requireArea(areaId);
        String normalized = requireName(name, "Area");
        Optional<Area> clash = areaRepository.findByNameIgnoreCase(normalized);
        if (clash.isPresent() && !Objects.equals(clash.get().getId(), area.getId())) {
            throw new IllegalArgumentException("An area called \"" + normalized + "\" already exists.");
        }
        area.setName(normalized);
        return areaRepository.save(area);
    }

    /** Enable/disable an Area. Disabling never deletes the Area or its societies. */
    @Transactional
    public Area setAreaActive(Long areaId, boolean active) {
        Area area = requireArea(areaId);
        if (!active && hasActiveSocieties(areaId)) {
            // Consistency rule: an Area that still offers active societies must not
            // be switched off, otherwise a buyer could pick a society whose parent
            // area is unavailable. The Admin disables the societies first.
            throw new IllegalStateException(
                    "This area still has active societies. Disable the societies first.");
        }
        area.setActive(active);
        return areaRepository.save(area);
    }

    @Transactional
    public Society createSociety(Long areaId, String name) {
        Area area = requireArea(areaId);
        String normalized = requireName(name, "Society");
        String key = Society.deriveNameKey(normalized);
        if (societyRepository.existsByAreaIdAndNameKey(area.getId(), key)) {
            throw new IllegalArgumentException(
                    "A society called \"" + normalized + "\" already exists under " + area.getName() + ".");
        }
        Society society = new Society(area, normalized);
        society.setActive(Boolean.TRUE);
        return societyRepository.save(society);
    }

    @Transactional
    public Society renameSociety(Long societyId, String name) {
        Society society = requireSociety(societyId);
        String normalized = requireName(name, "Society");
        String key = Society.deriveNameKey(normalized);
        Optional<Society> clash = societyRepository.findByAreaIdAndNameKey(society.getArea().getId(), key);
        if (clash.isPresent() && !Objects.equals(clash.get().getId(), society.getId())) {
            throw new IllegalArgumentException("A society called \"" + normalized
                    + "\" already exists under " + society.getArea().getName() + ".");
        }
        society.setName(normalized);
        return societyRepository.save(society);
    }

    /**
     * Enable/disable a Society.
     *
     * <p>Disabling keeps the row and every existing reference intact - historical
     * orders keep their snapshot, existing buyer profiles keep their location and
     * existing seller coverage is untouched. It only blocks NEW selections.</p>
     */
    @Transactional
    public Society setSocietyActive(Long societyId, boolean active) {
        Society society = requireSociety(societyId);
        society.setActive(active);
        return societyRepository.save(society);
    }

    // ==================== VALIDATION ====================

    /**
     * Validates a submitted Area/Society pair for a NEW selection.
     *
     * <p>Rejects unknown IDs, a Society that does not belong to the Area, an
     * inactive Society and an inactive Area. Returns the resolved Society so the
     * caller can persist its ID.</p>
     */
    @Transactional(readOnly = true)
    public Society requireSelectableLocation(Long areaId, Long societyId) {
        if (areaId == null) throw new IllegalArgumentException("Please choose your area.");
        if (societyId == null) throw new IllegalArgumentException("Please choose your community.");
        Area area = requireArea(areaId);
        Society society = requireSociety(societyId);
        if (!society.getArea().getId().equals(area.getId())) {
            throw new IllegalArgumentException("Please choose a community that belongs to the selected area.");
        }
        if (!area.isActive()) {
            throw new IllegalArgumentException("The selected area is no longer available. Please choose another.");
        }
        if (!society.isActive()) {
            throw new IllegalArgumentException("The selected community is no longer available. Please choose another.");
        }
        return society;
    }

    /**
     * Resolves a legacy free-text society name to exactly one record.
     *
     * <p>Returns {@code Optional.empty()} when no record has that name, and
     * refuses to guess when the same name exists under MORE than one Area - the
     * caller must keep such a value on the legacy string path rather than pick an
     * Area arbitrarily.</p>
     */
    @Transactional(readOnly = true)
    public Optional<Society> resolveLegacySociety(String societyName) {
        if (societyName == null || societyName.isBlank()) return Optional.empty();
        List<Society> matches = societyRepository.findByNameKey(Society.deriveNameKey(societyName));
        if (matches.size() != 1) return Optional.empty(); // unknown OR ambiguous
        return Optional.of(matches.get(0));
    }

    /** True only when the name resolves to exactly one record (never ambiguous). */
    @Transactional(readOnly = true)
    public boolean isUnambiguousLegacySociety(String societyName) {
        return resolveLegacySociety(societyName).isPresent();
    }

    // ==================== SELLER SERVICE COVERAGE ====================

    /** A seller's current explicit coverage, by ID. */
    @Transactional(readOnly = true)
    public Set<Society> getSellerCoverage(Kitchen kitchen) {
        if (kitchen == null) return Set.of();
        return new LinkedHashSet<>(kitchen.getServedSocieties());
    }

    /**
     * Saves a seller's COMPLETE final coverage selection.
     *
     * <p>The payload is the whole list of Society IDs the seller wants - not a
     * delta and not an Area wildcard. Every ID must exist and be active. The
     * denormalised {@code serviceAreas} display string is rebuilt from the saved
     * records so the two representations can never drift.</p>
     *
     * <p>Because the caller always sends the full selection, an unrelated profile
     * edit resends the previously saved IDs and changes nothing; and a society the
     * Admin adds later under an already-ticked Area is simply absent from the
     * payload, so it can never be silently adopted.</p>
     */
    @Transactional
    public void saveSellerCoverage(Kitchen kitchen, Collection<Long> societyIds) {
        if (kitchen == null) throw new IllegalArgumentException("Kitchen not found.");
        Set<Society> resolved = new LinkedHashSet<>();
        if (societyIds != null) {
            for (Long id : societyIds) {
                if (id == null) continue;
                Society society = societyRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown community selected."));
                if (!society.isActive()) {
                    throw new IllegalArgumentException(
                            "Community \"" + society.getName() + "\" is no longer available.");
                }
                resolved.add(society);
            }
        }
        kitchen.setServedSocieties(resolved);
        kitchen.setServiceAreas(toServiceAreaString(resolved));
        kitchenRepository.save(kitchen);
    }

    /** The display string for a coverage set: names, de-duplicated, sorted. */
    public static String toServiceAreaString(Collection<Society> societies) {
        if (societies == null || societies.isEmpty()) return "";
        Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Society s : societies) {
            if (s != null && s.getName() != null && !s.getName().isBlank()) names.add(s.getName().trim());
        }
        return String.join(",", names);
    }

    /**
     * "Does this seller serve this Society?" - the single eligibility answer.
     *
     * <p>Compared by identity (ID), not by display name, so two societies that
     * share a name under different Areas are never conflated.</p>
     */
    @Transactional(readOnly = true)
    public boolean serves(Kitchen kitchen, Society buyerSociety) {
        if (kitchen == null || buyerSociety == null) return false;
        for (Society covered : kitchen.getServedSocieties()) {
            if (covered != null && covered.getId() != null && covered.getId().equals(buyerSociety.getId())) {
                return true;
            }
        }
        return false;
    }

    // ==================== HELPERS ====================

    private static String requireName(String name, String label) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Please enter a " + label + " name.");
        if (normalized.length() > 160) {
            throw new IllegalArgumentException(label + " names must be 160 characters or fewer.");
        }
        return normalized;
    }

    private Area requireArea(Long areaId) {
        if (areaId == null) throw new IllegalArgumentException("Area id is required.");
        return areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Area not found."));
    }

    private Society requireSociety(Long societyId) {
        if (societyId == null) throw new IllegalArgumentException("Community id is required.");
        return societyRepository.findById(societyId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found."));
    }

    private boolean hasActiveSocieties(Long areaId) {
        return !societyRepository.findByAreaIdAndActiveTrueOrderByNameAsc(areaId).isEmpty();
    }

    /** Lower-case helper mirroring the entity's key derivation. */
    static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}



