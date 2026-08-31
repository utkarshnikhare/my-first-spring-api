package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductCreateDto {
    @NotBlank(message = "Item name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    private String priceUnit;
    private String imageUrl;
    private Boolean availableToday;
    private LocalDate availableDate;
    private String orderWindowStart;
    private String orderWindowEnd;
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
    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }
    public Integer getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public Boolean getIsPreorder() { return isPreorder; }
    public void setIsPreorder(Boolean isPreorder) { this.isPreorder = isPreorder; }
}
