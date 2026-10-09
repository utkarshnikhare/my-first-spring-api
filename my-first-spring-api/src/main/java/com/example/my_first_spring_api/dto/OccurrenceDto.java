package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.Occurrence;
import com.example.my_first_spring_api.model.OccurrenceStatus;
import java.time.LocalDate;

public class OccurrenceDto {
    private Long id;
    private Long scheduleId;
    private Long productId;
    private String productName;
    private LocalDate date;
    private Integer quantity;
    private String orderCloseTime;
    private String readyByTime;
    private OccurrenceStatus status;
    // Boolean (not boolean) so a PATCH body can distinguish "field absent" from
    // an explicit false: null = leave unchanged, FALSE = clear the flag.
    private Boolean soldOut;
    private Boolean ordersPaused;
    // Request-only: true means "set this day's quantity to No limit" (null alone
    // cannot mean that, because null also means "not provided" in a partial PATCH).
    private Boolean clearQuantity;

    /** No-arg constructor so Jackson can bind PATCH request bodies. */
    public OccurrenceDto() {}

    /** Helper constructor: builds the resolved DTO from an entity row.
     *  Annotated with @JsonIgnore so Jackson does not mistake this for a
     *  creator for request-body binding (its first param would otherwise be
     *  bound from the JSON and remain null, causing a NullPointerException
     *  when the constructor reads {@code o.getSchedule()}). */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public OccurrenceDto(Occurrence o, Integer resolvedQty, String resolvedClose, String resolvedReadyBy) {
        this.id = o.getId();
        this.scheduleId = o.getSchedule().getId();
        this.date = o.getOccurrenceDate();
        this.quantity = resolvedQty;
        this.orderCloseTime = resolvedClose;
        this.readyByTime = resolvedReadyBy;
        this.status = o.getStatus();
        this.soldOut = o.isSoldOut();
        this.ordersPaused = o.isOrdersPaused();
        if (o.getSchedule() != null && o.getSchedule().getProduct() != null) {
            this.productId = o.getSchedule().getProduct().getId();
            this.productName = o.getSchedule().getProduct().getName();
        }
    }

    // Getters
    public Long getId() { return id; }
    public Long getScheduleId() { return scheduleId; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public LocalDate getDate() { return date; }
    public Integer getQuantity() { return quantity; }
    public String getOrderCloseTime() { return orderCloseTime; }
    public String getReadyByTime() { return readyByTime; }
    public OccurrenceStatus getStatus() { return status; }
    public Boolean getSoldOut() { return soldOut; }
    public Boolean getOrdersPaused() { return ordersPaused; }
    public Boolean getClearQuantity() { return clearQuantity; }

    /** Display convenience: a null quantity means exactly "No limit". */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNoLimit() { return quantity == null; }

    // Setters (Jackson binding for request bodies)
    public void setId(Long id) { this.id = id; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setDate(LocalDate date) { this.date = date; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setOrderCloseTime(String orderCloseTime) { this.orderCloseTime = orderCloseTime; }
    public void setReadyByTime(String readyByTime) { this.readyByTime = readyByTime; }
    public void setStatus(OccurrenceStatus status) { this.status = status; }
    public void setSoldOut(Boolean soldOut) { this.soldOut = soldOut; }
    public void setOrdersPaused(Boolean ordersPaused) { this.ordersPaused = ordersPaused; }
    public void setClearQuantity(Boolean clearQuantity) { this.clearQuantity = clearQuantity; }
}
