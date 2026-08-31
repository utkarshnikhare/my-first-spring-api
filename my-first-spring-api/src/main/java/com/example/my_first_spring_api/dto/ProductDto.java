package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductDto {
    private Long id;
    private Long kitchenId;
    private String kitchenName;
    private String name;
    private String description;
    private BigDecimal price;
    private String priceUnit;
    private String imageUrl;
    private Boolean availableToday;
    private LocalDate availableDate;
    private String orderWindowStart;
    private String orderWindowEnd;
    private Integer maxQuantity;
    private Integer remainingQuantity;
    private Double rating;
    private Boolean isPreorder;

    public ProductDto() {}

    public ProductDto(Long id, Long kitchenId, String kitchenName, String name, String description,
                      BigDecimal price, String imageUrl, Boolean availableToday, Double rating) {
        this.id = id;
        this.kitchenId = kitchenId;
        this.kitchenName = kitchenName;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.availableToday = availableToday;
        this.rating = rating;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getKitchenName() { return kitchenName; }
    public void setKitchenName(String kitchenName) { this.kitchenName = kitchenName; }
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
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Boolean getIsPreorder() { return isPreorder; }
    public void setIsPreorder(Boolean isPreorder) { this.isPreorder = isPreorder; }
}
