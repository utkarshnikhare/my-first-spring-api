package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Per-day override belonging to ONE {@link Occurrence}.
 *
 * <p>PHASE 1 foundation only. Core acceptance rule: an override affects ONLY
 * its own occurrence and NEVER mutates the {@link RecurringSchedule} defaults.
 * Null field = "not overridden, inherit occurrence/schedule value"; the
 * resolution logic itself belongs to a later phase (Phase 4), but the model
 * already distinguishes "no override" (null) from an explicit value.
 */
@Entity
@Table(name = "occurrence_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_override_occurrence",
                columnNames = {"occurrence_id"}),
        indexes = {
                @Index(name = "idx_override_occurrence", columnList = "occurrence_id")
        })
public class OccurrenceOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The single occurrence this override applies to. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "occurrence_id", nullable = false, unique = true)
    private Occurrence occurrence;

    /** Quantity override; null = not overridden. */
    @Column(name = "quantity")
    private Integer quantity;

    /** Distinguishes explicit unlimited (true + null) from inheritance. */
    @Column(name = "quantity_overridden", nullable = false)
    private boolean quantityOverridden;

    /** Order-close override (HH:mm); null = not overridden. */
    @Column(name = "order_close_time")
    private String orderCloseTime;

    /** Delivery/ready-by override; null = not overridden. */
    @Column(name = "ready_by_time")
    private String readyByTime;

    /** Sold-out override; null = not overridden. */
    @Column(name = "sold_out")
    private Boolean soldOut;

    /** Orders-closed/paused override; null = not overridden. */
    @Column(name = "orders_paused")
    private Boolean ordersPaused;

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

    public OccurrenceOverride() {}

    public OccurrenceOverride(Occurrence occurrence) {
        this.occurrence = occurrence;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Occurrence getOccurrence() { return occurrence; }
    public void setOccurrence(Occurrence o) { this.occurrence = o; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer q) { this.quantity = q; this.quantityOverridden = true; }
    public boolean isQuantityOverridden() { return quantityOverridden; }
    public void setQuantityOverridden(boolean quantityOverridden) { this.quantityOverridden = quantityOverridden; }
    public String getOrderCloseTime() { return orderCloseTime; }
    public void setOrderCloseTime(String t) { this.orderCloseTime = t; }
    public String getReadyByTime() { return readyByTime; }
    public void setReadyByTime(String t) { this.readyByTime = t; }
    public Boolean getSoldOut() { return soldOut; }
    public void setSoldOut(Boolean b) { this.soldOut = b; }
    public Boolean getOrdersPaused() { return ordersPaused; }
    public void setOrdersPaused(Boolean b) { this.ordersPaused = b; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
