package com.example.my_first_spring_api.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

public class RecurringScheduleDto {
    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    private Set<DayOfWeek> recurrenceWeekdays;
    private Integer defaultQuantity;
    private Boolean clearDefaultQuantity;
    private String defaultOrderCloseTime;
    private String defaultReadyByTime;
    private Boolean ongoing;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Set<DayOfWeek> getRecurrenceWeekdays() { return recurrenceWeekdays; }
    public void setRecurrenceWeekdays(Set<DayOfWeek> recurrenceWeekdays) { this.recurrenceWeekdays = recurrenceWeekdays; }
    public Integer getDefaultQuantity() { return defaultQuantity; }
    public void setDefaultQuantity(Integer defaultQuantity) { this.defaultQuantity = defaultQuantity; }
    public Boolean getClearDefaultQuantity() { return clearDefaultQuantity; }
    public void setClearDefaultQuantity(Boolean clearDefaultQuantity) { this.clearDefaultQuantity = clearDefaultQuantity; }
    public String getDefaultOrderCloseTime() { return defaultOrderCloseTime; }
    public void setDefaultOrderCloseTime(String defaultOrderCloseTime) { this.defaultOrderCloseTime = defaultOrderCloseTime; }
    public String getDefaultReadyByTime() { return defaultReadyByTime; }
    public void setDefaultReadyByTime(String defaultReadyByTime) { this.defaultReadyByTime = defaultReadyByTime; }
    public Boolean getOngoing() { return ongoing; }
    public void setOngoing(Boolean ongoing) { this.ongoing = ongoing; }
}
