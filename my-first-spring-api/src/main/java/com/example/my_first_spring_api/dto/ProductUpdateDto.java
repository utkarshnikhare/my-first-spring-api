package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for partial product updates (PUT /api/seller/products/{id}).
 * Unlike ProductCreateDto, no field is mandatory: the service layer already
 * applies only non-null fields, so validation must not reject partial bodies.
 */
public class ProductUpdateDto {
    private String name;
    private String description;
    private BigDecimal price;
    private String priceUnit;
    private String imageUrl;
    private Boolean availableToday;
    private LocalDate availableDate;
    private String orderWindowStart;
    private String orderWindowEnd;
    private String cutoffTime;
    private String readyByTime;
    private Integer maxQuantity;
    private Integer remainingQuantity;
    private Boolean isPreorder;

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
    public Boolean getAvailableToday() { return availableToday; }
    public void setAvailableToday(Boolean availableToday) { this.availableToday = availableToday; }
    public LocalDate getAvailableDate() { return availableDate; }
    public void setAvailableDate(LocalDate availableDate) { this.availableDate = availableDate; }
    public String getOrderWindowStart() { return orderWindowStart; }
    public void setOrderWindowStart(String orderWindowStart) { this.orderWindowStart = orderWindowStart; }
    public String getOrderWindowEnd() { return orderWindowEnd; }
    public void setOrderWindowEnd(String orderWindowEnd) { this.orderWindowEnd = orderWindowEnd; }
    public String getCutoffTime() { return cutoffTime; }
    public void setCutoffTime(String cutoffTime) { this.cutoffTime = cutoffTime; }
    public String getReadyByTime() { return readyByTime; }
    public void setReadyByTime(String readyByTime) { this.readyByTime = readyByTime; }
    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }
    public Integer getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public Boolean getIsPreorder() { return isPreorder; }
    public void setIsPreorder(Boolean isPreorder) { this.isPreorder = isPreorder; }
}