package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One stored selling date of a {@link RecurringSchedule} (decision B2:
 * STORED OCCURRENCE ROWS are canonical).
 *
 * <p>PHASE 1 foundation only. Each row is INDEPENDENT: a future override or
 * status change on Wednesday never touches Monday or Friday — isolation is
 * enforced by one row per date plus the (schedule, date) unique constraint.
 *
 * <p>Quantity convention mirrors Product/schedule: null quantity = "No limit".
 */
@Entity
@Table(name = "occurrences",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_occurrence_schedule_date",
                columnNames = {"schedule_id", "occurrence_date"}),
        indexes = {
                @Index(name = "idx_occurrence_schedule", columnList = "schedule_id"),
                @Index(name = "idx_occurrence_date", columnList = "occurrence_date")
        })
public class Occurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private RecurringSchedule schedule;

    /** The selling date this occurrence represents. */
    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    /** Per-occurrence quantity snapshot; null = "No limit". */
    @Column(name = "quantity")
    private Integer quantity;

    /** Per-occurrence order-close time (HH:mm). */
    @Column(name = "order_close_time")
    private String orderCloseTime;

    /** Per-occurrence delivery/ready-by text. */
    @Column(name = "ready_by_time")
    private String readyByTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OccurrenceStatus status = OccurrenceStatus.SCHEDULED;

    /** Per-day sold-out flag (Sold Out Today scope). */
    @Column(name = "sold_out", nullable = false)
    private boolean soldOut = false;

    /** Per-day orders-closed/paused flag (Close Orders Today scope). */
    @Column(name = "orders_paused", nullable = false)
    private boolean ordersPaused = false;

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

    public Occurrence() {}

    public Occurrence(RecurringSchedule schedule, LocalDate occurrenceDate) {
        this.schedule = schedule;
        this.occurrenceDate = occurrenceDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RecurringSchedule getSchedule() { return schedule; }
    public void setSchedule(RecurringSchedule s) { this.schedule = s; }
    public LocalDate getOccurrenceDate() { return occurrenceDate; }
    public void setOccurrenceDate(LocalDate d) { this.occurrenceDate = d; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer q) { this.quantity = q; }

    @Transient
    public boolean isNoLimit() { return quantity == null; }

    public String getOrderCloseTime() { return orderCloseTime; }
    public void setOrderCloseTime(String t) { this.orderCloseTime = t; }
    public String getReadyByTime() { return readyByTime; }
    public void setReadyByTime(String t) { this.readyByTime = t; }
    public OccurrenceStatus getStatus() { return status; }
    public void setStatus(OccurrenceStatus s) { this.status = s; }
    public boolean isSoldOut() { return soldOut; }
    public void setSoldOut(boolean b) { this.soldOut = b; }
    public boolean isOrdersPaused() { return ordersPaused; }
    public void setOrdersPaused(boolean b) { this.ordersPaused = b; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
