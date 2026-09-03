package com.example.my_first_spring_api.dto;

public class KitchenDto {
    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String shortDescription;
    private String imageUrl;
    private String society;
    private String building;
    private String whatsappLink;
    private String instagramLink;
    private String galleryImages;
    private String upiId;
    private Double rating;
    private Boolean availableToday;
    private String orderDeadline;
    private Long sellerId;

    public KitchenDto() {}

    public KitchenDto(Long id, String name, String displayName, String description, String imageUrl,
                      Double rating, Boolean availableToday, Long sellerId) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.imageUrl = imageUrl;
        this.rating = rating;
        this.availableToday = availableToday;
        this.sellerId = sellerId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getSociety() { return society; }
    public void setSociety(String society) { this.society = society; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public String getWhatsappLink() { return whatsappLink; }
    public void setWhatsappLink(String whatsappLink) { this.whatsappLink = whatsappLink; }
    public String getInstagramLink() { return instagramLink; }
    public void setInstagramLink(String instagramLink) { this.instagramLink = instagramLink; }
    public String getGalleryImages() { return galleryImages; }
    public void setGalleryImages(String galleryImages) { this.galleryImages = galleryImages; }
    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Boolean getAvailableToday() { return availableToday; }
    public void setAvailableToday(Boolean availableToday) { this.availableToday = availableToday; }
    public String getOrderDeadline() { return orderDeadline; }
    public void setOrderDeadline(String orderDeadline) { this.orderDeadline = orderDeadline; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
}
