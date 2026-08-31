package com.example.my_first_spring_api.dto;

public class KitchenUpdateDto {
    private String name;
    private String displayName;
    private String description;
    private String shortDescription;
    private String imageUrl;
    private String society;
    private String building;
    private String whatsappLink;
    private String instagramLink;
    private String upiId;
    private Boolean availableToday;

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
    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
    public Boolean getAvailableToday() { return availableToday; }
    public void setAvailableToday(Boolean availableToday) { this.availableToday = availableToday; }
}