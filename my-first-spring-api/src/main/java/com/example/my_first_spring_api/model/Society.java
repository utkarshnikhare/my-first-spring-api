package com.example.my_first_spring_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One society inside exactly one {@link Area}.
 *
 * <p>This is the authoritative, ID-backed location master for the whole platform.
 * Display names are NOT the identity used for relationships: buyers store a
 * reference to a Society (and its Area), sellers store an explicit set of Society
 * references as their service coverage, and the eligibility check compares those
 * references. A society name may legitimately exist under two different Areas -
 * the {@link #area} is what disambiguates them.</p>
 *
 * <p>Case-insensitive uniqueness inside an Area is enforced in the database via
 * {@link #nameKey} (the lower-cased name), so "Kingsbury" and "kingsbury" can
 * never both exist under the same Area, while "Kingsbury" under one Area and
 * "Kingsbury" under another are separate records.</p>
 *
 * <p>Records are disabled rather than deleted: buyers, sellers and historical
 * orders keep referencing them. Disabling never rewrites a historical order's
 * location snapshot.</p>
 */
@Entity
@Table(name = "societies", uniqueConstraints = @UniqueConstraint(columnNames = {"area_id", "name_key"}))
public class Society {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    /** Display spelling, preserved exactly as first entered. */
    @Column(name = "name", nullable = false, length = 160)
    private String name;

    /**
     * Lower-cased {@link #name} used as the database uniqueness key so that
     * duplicate detection is case-insensitive without a vendor-specific index.
     */
    @Column(name = "name_key", nullable = false, length = 160)
    private String nameKey;

    /**
     * Inactive societies stay readable for historical data but cannot be chosen
     * for a NEW buyer profile or NEW seller coverage.
     */
    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Society() {}

    public Society(Area area, String name) {
        this.area = area;
        setName(name);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (nameKey == null || nameKey.isBlank()) nameKey = deriveNameKey(name);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        nameKey = deriveNameKey(name);
    }

    /** Lower-cased, trimmed identity key used for case-insensitive comparison. */
    public static String deriveNameKey(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Area getArea() { return area; }
    public void setArea(Area area) { this.area = area; }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
        this.nameKey = deriveNameKey(this.name);
    }

    public String getNameKey() { return nameKey; }
    public void setNameKey(String nameKey) { this.nameKey = nameKey; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public boolean isActive() { return !Boolean.FALSE.equals(active); }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Society)) return false;
        Society society = (Society) o;
        // Identity is the persisted ID; two unsaved instances are never equal.
        return id != null && id.equals(society.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return "Society{id=" + id + ", name='" + name + "', area=" + (area == null ? null : area.getName())
                + ", active=" + active + "}";
    }
}
