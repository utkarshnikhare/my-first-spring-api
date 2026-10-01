package com.example.my_first_spring_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * A locality that groups the societies inside it, e.g. "Charholi / Lohegaon".
 *
 * <p>The Area -&gt; Society relationship is expressed by {@link Society#getArea()},
 * NOT by a list of society names held on this record. Holding display strings here
 * would give the same society two competing identities, so the relationship is
 * stored once, on {@link Society}, keyed by the Society's stable generated id.</p>
 *
 * <p>Areas are disabled rather than deleted so that existing buyers, sellers and
 * historical orders keep resolving. A disabled Area cannot be selected for new
 * records but continues to describe historical ones.</p>
 */
@Entity
@Table(name = "areas", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Area {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 160)
    private String name;

    /** Inactive areas are hidden from new selections but never deleted. */
    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Area() {}

    public Area(String name) {
        this.name = name;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (name != null) name = name.trim();
        if (active == null) active = Boolean.TRUE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (name != null) name = name.trim();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public boolean isActive() { return !Boolean.FALSE.equals(active); }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return "Area{id=" + id + ", name='" + name + "', active=" + active + "}"; }
}
