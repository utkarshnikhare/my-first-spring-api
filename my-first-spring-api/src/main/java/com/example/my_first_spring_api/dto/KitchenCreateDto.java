package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.NotBlank;

public class KitchenCreateDto {
    @NotBlank(message = "Kitchen URL name is required")
    private String name;

    @NotBlank(message = "Display name is required")
    private String displayName;

    private String description;
    private String shortDescription;
    private String imageUrl;
    private String society;
    private String serviceAreas;
    /**
     * Authoritative service-area coverage as Society IDs. When non-null this
     * REPLACES {@link #serviceAreas} as the write source and is applied through
     * {@code LocationService.saveSellerCoverage(kitchen, areaId, societyIds)}; the
     * denormalised display string is rebuilt from the saved records. The legacy
     * string field is retained only so older callers keep working - the current
     * Seller/Admin UIs send IDs.
     */
    private Long areaId;
    private java.util.List<Long> societyIds;
    private String building;
    private String whatsappLink;
    private String instagramLink;
    private String upiId;
    private Boolean availableToday;
    private String sellerType;

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
    public String getServiceAreas() { return serviceAreas; }
    public void setServiceAreas(String serviceAreas) { this.serviceAreas = serviceAreas; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public java.util.List<Long> getSocietyIds() { return societyIds; }
    public void setSocietyIds(java.util.List<Long> societyIds) { this.societyIds = societyIds; }
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
    public String getSellerType() { return sellerType; }
    public void setSellerType(String sellerType) { this.sellerType = sellerType; }
}
