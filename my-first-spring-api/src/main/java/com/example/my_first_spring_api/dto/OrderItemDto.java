package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;

public class OrderItemDto {
    private Long id;
    private Long productId;
    private String productName;
    private String imageUrl;
    private Integer quantity;
    private BigDecimal price;

    public OrderItemDto() {}

    public OrderItemDto(Long id, Long productId, String productName, String imageUrl,
                        Integer quantity, BigDecimal price) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.imageUrl = imageUrl;
        this.quantity = quantity;
        this.price = price;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
