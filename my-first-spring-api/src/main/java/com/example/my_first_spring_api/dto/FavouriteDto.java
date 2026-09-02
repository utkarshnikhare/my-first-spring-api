package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;

public class FavouriteDto {
    private Long id;
    /** KITCHEN | FOOD */
    private String type;
    private Long kitchenId;
    private Long productId;
    private String name;
    private String imageUrl;
    private String subtitle;
    private BigDecimal price;
    private String kitchenName;

    public FavouriteDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getKitchenName() { return kitchenName; }
    public void setKitchenName(String kitchenName) { this.kitchenName = kitchenName; }
}
