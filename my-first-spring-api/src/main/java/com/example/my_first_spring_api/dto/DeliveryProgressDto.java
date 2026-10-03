package com.example.my_first_spring_api.dto;

import java.time.LocalDate;

/**
 * Delivery progress for ONE offering on ONE date, plus the exact server-side
 * scope of the "Mark All Delivered" bulk action.
 *
 * <p>Every number here is calculated from ACTIVE (non-draft, non-cancelled)
 * orders only. {@link #bulkScopeOrderCount} is deliberately server-calculated
 * rather than derived from the rows the browser happens to be rendering: the
 * confirmation dialog must quote the count the backend will really change, not
 * the count of whatever subset the current filters happen to show.</p>
 */
public class DeliveryProgressDto {

    private Long productId;
    private String productName;
    private LocalDate date;

    /** Active (non-cancelled) orders containing this offering on this date. */
    private int activeOrderCount;
    /** Of those, how many the seller has already recorded as Delivered. */
    private int deliveredCount;
    /** activeOrderCount - deliveredCount. Zero means "All deliveries completed". */
    private int remainingCount;

    /**
     * Active orders that the bulk action would newly mark Delivered (i.e. active
     * and not yet delivered). Already-delivered and cancelled orders are
     * excluded, so this number never overstates the work the action will do.
     */
    private int bulkScopeOrderCount;
    /**
     * Active orders already Delivered; kept Delivered and left untouched by the bulk.
     */
    private int bulkAlreadyDeliveredCount;

    /**
     * Orders THIS bulk call actually newly marked Delivered.
     *
     * <p>Zero for a plain progress read, and zero for a bulk call that found
     * nothing left to change (every active order was already Delivered, e.g.
     * another tab got there first). Exposed so the success message quotes the
     * server's real work instead of the figure the browser predicted.</p>
     */
    private int appliedCount;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public int getActiveOrderCount() { return activeOrderCount; }
    public void setActiveOrderCount(int activeOrderCount) { this.activeOrderCount = activeOrderCount; }
    public int getDeliveredCount() { return deliveredCount; }
    public void setDeliveredCount(int deliveredCount) { this.deliveredCount = deliveredCount; }
    public int getRemainingCount() { return remainingCount; }
    public void setRemainingCount(int remainingCount) { this.remainingCount = remainingCount; }
    public int getBulkScopeOrderCount() { return bulkScopeOrderCount; }
    public void setBulkScopeOrderCount(int bulkScopeOrderCount) { this.bulkScopeOrderCount = bulkScopeOrderCount; }
    public int getBulkAlreadyDeliveredCount() { return bulkAlreadyDeliveredCount; }
    public void setBulkAlreadyDeliveredCount(int bulkAlreadyDeliveredCount) { this.bulkAlreadyDeliveredCount = bulkAlreadyDeliveredCount; }
    public int getAppliedCount() { return appliedCount; }
    public void setAppliedCount(int appliedCount) { this.appliedCount = appliedCount; }
}