package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Authoritative read model for the Seller "Edit Offering" screen (Requirement 5).
 *
 * <p>It mirrors exactly the fields the edit form can show, resolves the effective
 * Orders Close value (orderWindowEnd with the legacy cutoffTime fallback) and
 * reports whether the offering is already locked by real customer orders.
 * Draft carts are excluded, so an abandoned buyer draft never locks the screen.
 */
public class SellerOfferingEditDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String priceUnit;
    private String imageUrl;
    private String category;
    private LocalDate availableDate;
    private String orderWindowStart;
    private String orderWindowEnd;
    private String readyByTime;
    private Integer maxQuantity;
    private Integer remainingQuantity;
    private Integer bookedQuantity;
    private Boolean isPreorder;
    private Boolean soldOut;
    private Boolean ordersPaused;
    private String lifecycleState;
    private Boolean hasOrders;
    private Integer orderCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getPriceUnit() { return priceUnit; }
    public void setPriceUnit(String priceUnit) { this.priceUnit = priceUnit; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getAvailableDate() { return availableDate; }
    public void setAvailableDate(LocalDate availableDate) { this.availableDate = availableDate; }
    public String getOrderWindowStart() { return orderWindowStart; }
    public void setOrderWindowStart(String orderWindowStart) { this.orderWindowStart = orderWindowStart; }
    public String getOrderWindowEnd() { return orderWindowEnd; }
    public void setOrderWindowEnd(String orderWindowEnd) { this.orderWindowEnd = orderWindowEnd; }
    public String getReadyByTime() { return readyByTime; }
    public void setReadyByTime(String readyByTime) { this.readyByTime = readyByTime; }
    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }
    public Integer getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public Integer getBookedQuantity() { return bookedQuantity; }
    public void setBookedQuantity(Integer bookedQuantity) { this.bookedQuantity = bookedQuantity; }
    public Boolean getIsPreorder() { return isPreorder; }
    public void setIsPreorder(Boolean isPreorder) { this.isPreorder = isPreorder; }
    public Boolean getSoldOut() { return soldOut; }
    public void setSoldOut(Boolean soldOut) { this.soldOut = soldOut; }
    public Boolean getOrdersPaused() { return ordersPaused; }
    public void setOrdersPaused(Boolean ordersPaused) { this.ordersPaused = ordersPaused; }
    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String lifecycleState) { this.lifecycleState = lifecycleState; }
    public Boolean getHasOrders() { return hasOrders; }
    public void setHasOrders(Boolean hasOrders) { this.hasOrders = hasOrders; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
}
