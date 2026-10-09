package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.OccurrenceStatus;
import com.example.my_first_spring_api.model.RecurringSchedule;
import com.example.my_first_spring_api.model.RecurringScheduleStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Seller-facing card for one recurring schedule (RECURRING dashboard tab and
 * the Manage Schedule editor).
 *
 * <p>Deliberately NOT the entity: the product association is LAZY, so the card
 * copies the display fields inside the transactional service that builds it.
 * {@code defaultQuantity == null} means exactly "No limit" (never 0 or a fake
 * cap) — see {@link #isNoLimit()}.
 */
public class ScheduleCardDto {
    private Long scheduleId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private Set<DayOfWeek> recurrenceWeekdays;
    private Integer defaultQuantity;
    private String defaultOrderCloseTime;
    private String defaultReadyByTime;
    private RecurringScheduleStatus status;
    private boolean ongoing;
    private long occurrenceCount;
    private OccurrenceStatus nextOccurrenceStatus;
    /**
     * First not-yet-past selling date (null when the schedule has fully run
     * out). The RECURRING card shows "Next occurrence: Fri 9 Oct" from this.
     */
    private LocalDate nextOccurrenceDate;

    /** No-arg constructor for Jackson. */
    public ScheduleCardDto() {}

    /** Copies the schedule's own fields; product fields are set by the service. */
    public ScheduleCardDto(RecurringSchedule schedule, long occurrenceCount) {
        this.scheduleId = schedule.getId();
        this.startDate = schedule.getStartDate();
        this.endDate = schedule.getEndDate();
        this.recurrenceWeekdays = schedule.getRecurrenceWeekdays();
        this.defaultQuantity = schedule.getDefaultQuantity();
        this.defaultOrderCloseTime = schedule.getDefaultOrderCloseTime();
        this.defaultReadyByTime = schedule.getDefaultReadyByTime();
        this.status = schedule.getStatus();
        this.ongoing = schedule.isOngoing();
        this.occurrenceCount = occurrenceCount;
    }

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductImageUrl() { return productImageUrl; }
    public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Set<DayOfWeek> getRecurrenceWeekdays() { return recurrenceWeekdays; }
    public void setRecurrenceWeekdays(Set<DayOfWeek> recurrenceWeekdays) { this.recurrenceWeekdays = recurrenceWeekdays; }
    public Integer getDefaultQuantity() { return defaultQuantity; }
    public void setDefaultQuantity(Integer defaultQuantity) { this.defaultQuantity = defaultQuantity; }
    public String getDefaultOrderCloseTime() { return defaultOrderCloseTime; }
    public void setDefaultOrderCloseTime(String t) { this.defaultOrderCloseTime = t; }
    public String getDefaultReadyByTime() { return defaultReadyByTime; }
    public void setDefaultReadyByTime(String t) { this.defaultReadyByTime = t; }
    public RecurringScheduleStatus getStatus() { return status; }
    public void setStatus(RecurringScheduleStatus status) { this.status = status; }
    public boolean isOngoing() { return ongoing; }
    public void setOngoing(boolean ongoing) { this.ongoing = ongoing; }
    public long getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(long occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public OccurrenceStatus getNextOccurrenceStatus() { return nextOccurrenceStatus; }
    public void setNextOccurrenceStatus(OccurrenceStatus s) { this.nextOccurrenceStatus = s; }
    public LocalDate getNextOccurrenceDate() { return nextOccurrenceDate; }
    public void setNextOccurrenceDate(LocalDate d) { this.nextOccurrenceDate = d; }

    /** Blank quantity on the Manage Schedule form means exactly "No limit". */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNoLimit() { return defaultQuantity == null; }
}
