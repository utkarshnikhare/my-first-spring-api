package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Recurring configuration/defaults for ONE existing {@link Product} offering.
 *
 * <p>PHASE 1 foundation only. ADDITIVE branch: one-time Product rows never
 * require a schedule, and Product's one-time availability/quantity semantics
 * are unchanged. The schedule OWNS the recurring defaults (decision B3).
 *
 * <p>Quantity: null defaultQuantity = "No limit" (same as Product
 * maxQuantity). Ongoing (decision B4): endDate stores the materialised
 * 90-day horizon, never an unbounded range.
 */
@Entity
@Table(name = "recurring_schedules")
public class RecurringSchedule {

    /** Internal cap for "Ongoing": 90 days from the schedule start (B4). */
    public static final int ONGOING_INTERNAL_DAYS_CAP = 90;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The offering this schedule configures. One Product, at most one schedule. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    /** First selling date (inclusive). */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Last selling date (inclusive); Ongoing stores start+90 days. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** CSV of ISO weekday numbers 1..7 ascending, e.g. "1,3,5" for Mon/Wed/Fri. */
    @Column(name = "recurrence_days", nullable = false)
    private String recurrenceDays;

    /** Default per-occurrence quantity; null = "No limit". */
    @Column(name = "default_quantity")
    private Integer defaultQuantity;

    /** Default per-occurrence order-close time (HH:mm). */
    @Column(name = "default_order_close_time")
    private String defaultOrderCloseTime;

    /** Default per-occurrence delivery/ready-by text. */
    @Column(name = "default_ready_by_time")
    private String defaultReadyByTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecurringScheduleStatus status = RecurringScheduleStatus.ACTIVE;

    /** True when the server, rather than a client end date, owns the horizon. */
    @Column(name = "ongoing", nullable = false)
    private boolean ongoing;

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

    public RecurringSchedule() {}

    public RecurringSchedule(Product product, LocalDate startDate, LocalDate endDate,
                             Set<DayOfWeek> weekdays) {
        this.product = product;
        this.startDate = startDate;
        this.endDate = endDate;
        setRecurrenceWeekdays(weekdays);
    }

    /** Ongoing factory: end date = start + 90-day internal horizon. */
    public static RecurringSchedule ongoing(Product product, LocalDate startDate,
                                             Set<DayOfWeek> weekdays) {
        RecurringSchedule schedule = new RecurringSchedule(product, startDate,
                startDate.plusDays(ONGOING_INTERNAL_DAYS_CAP - 1L), weekdays);
        schedule.setOngoing(true);
        return schedule;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate d) { this.startDate = d; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate d) { this.endDate = d; }
    public String getRecurrenceDays() { return recurrenceDays; }
    public void setRecurrenceDays(String s) { this.recurrenceDays = s; }
    public Integer getDefaultQuantity() { return defaultQuantity; }
    public void setDefaultQuantity(Integer q) { this.defaultQuantity = q; }

    @Transient
    public boolean isNoLimit() { return defaultQuantity == null; }

    public String getDefaultOrderCloseTime() { return defaultOrderCloseTime; }
    public void setDefaultOrderCloseTime(String t) { this.defaultOrderCloseTime = t; }
    public String getDefaultReadyByTime() { return defaultReadyByTime; }
    public void setDefaultReadyByTime(String t) { this.defaultReadyByTime = t; }
    public RecurringScheduleStatus getStatus() { return status; }
    public void setStatus(RecurringScheduleStatus s) { this.status = s; }
    public boolean isOngoing() { return ongoing; }
    public void setOngoing(boolean ongoing) { this.ongoing = ongoing; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }

    /** Weekdays as an ordered set (empty only before persistence). */
    @Transient
    public Set<DayOfWeek> getRecurrenceWeekdays() {
        Set<DayOfWeek> out = new TreeSet<>();
        if (recurrenceDays == null || recurrenceDays.isBlank()) return out;
        for (String part : recurrenceDays.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) out.add(DayOfWeek.of(Integer.parseInt(p)));
        }
        return out;
    }

    /** Stores weekdays ascending as "1,3,5". Rejects null/empty. */
    public void setRecurrenceWeekdays(Set<DayOfWeek> weekdays) {
        if (weekdays == null || weekdays.isEmpty()) {
            throw new IllegalArgumentException("recurrence weekdays must contain at least one day");
        }
        EnumSet<DayOfWeek> copy = EnumSet.copyOf(weekdays);
        this.recurrenceDays = copy.stream()
                .map(d -> String.valueOf(d.getValue()))
                .collect(Collectors.joining(","));
    }
}
