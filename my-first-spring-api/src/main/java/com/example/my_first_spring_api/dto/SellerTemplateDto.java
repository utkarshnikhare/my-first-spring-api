package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class SellerTemplateDto {
    private Long id;

    @NotBlank(message = "Template name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    private String priceUnit;
    private String imageUrl;

    @Positive(message = "Max quantity must be at least 1 (leave empty for unlimited)")
    private Integer maxQuantity;
    private String cutoffTime;
    private String readyByTime;
    private String orderWindowStart;
    private String orderWindowEnd;
    private String category;

    public SellerTemplateDto() {}

    public SellerTemplateDto(Long id, String name, String description, BigDecimal price,
                             String priceUnit, String imageUrl, Integer maxQuantity,
                             String cutoffTime, String readyByTime,
                             String orderWindowStart, String orderWindowEnd, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.priceUnit = priceUnit;
        this.imageUrl = imageUrl;
        this.maxQuantity = maxQuantity;
        this.cutoffTime = cutoffTime;
        this.readyByTime = readyByTime;
        this.orderWindowStart = orderWindowStart;
        this.orderWindowEnd = orderWindowEnd;
        this.category = category;
    }

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
    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }
    public String getCutoffTime() { return cutoffTime; }
    public void setCutoffTime(String cutoffTime) { this.cutoffTime = cutoffTime; }
    public String getReadyByTime() { return readyByTime; }
    public void setReadyByTime(String readyByTime) { this.readyByTime = readyByTime; }
    public String getOrderWindowStart() { return orderWindowStart; }
    public void setOrderWindowStart(String orderWindowStart) { this.orderWindowStart = orderWindowStart; }
    public String getOrderWindowEnd() { return orderWindowEnd; }
    public void setOrderWindowEnd(String orderWindowEnd) { this.orderWindowEnd = orderWindowEnd; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}

