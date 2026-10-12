package com.example.my_first_spring_api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A retired kitchen slug kept alive as a permanent redirect.
 *
 * <p>When an Admin changes a kitchen's slug, the previous value is stored here
 * so every already-shared {@code /kitchen/{oldSlug}} link keeps resolving to the
 * same kitchen instead of 404-ing. Rows are never deleted - a public URL that
 * was once handed out must never break.</p>
 */
@Entity
@Table(name = "kitchen_slug_aliases")
public class KitchenSlugAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "old_slug", nullable = false, unique = true)
    private String oldSlug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kitchen_id", nullable = false)
    private Kitchen kitchen;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public KitchenSlugAlias() {}

    public KitchenSlugAlias(String oldSlug, Kitchen kitchen) {
        this.oldSlug = oldSlug;
        this.kitchen = kitchen;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOldSlug() { return oldSlug; }
    public void setOldSlug(String oldSlug) { this.oldSlug = oldSlug; }
    public Kitchen getKitchen() { return kitchen; }
    public void setKitchen(Kitchen kitchen) { this.kitchen = kitchen; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
