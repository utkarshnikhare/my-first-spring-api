package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class SellerRegistrationRequestDto {
    @NotBlank
    @Size(max = 120)
    private String sellerName;

    @NotBlank
    @Pattern(regexp = "\\d{10}", message = "Enter a 10-digit mobile number.")
    private String mobileNumber;

    @NotBlank
    @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters.")
    private String password;

    @NotBlank
    @Pattern(regexp = "\\d{10}", message = "Enter a 10-digit WhatsApp number.")
    private String whatsappNumber;

    @Pattern(regexp = "^$|\\d{10}", message = "Enter a 10-digit alternate contact number.")
    private String alternateContact;

    @NotBlank
    @Size(max = 120)
    private String kitchenName;

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*",
            message = "Use letters, numbers, and single hyphens for the kitchen URL.")
    @Size(min = 3, max = 80)
    private String kitchenSlug;

    @NotBlank
    @Size(max = 250)
    private String speciality;

    @NotBlank
    @Pattern(regexp = "(?i)KITCHEN|HOMEMADE_PRODUCTS|BOTH")
    private String sellerCategory;

    @Size(max = 500)
    private String shortDescription;

    @Size(max = 255)
    private String instagramLink;

    @NotNull
    private Long primaryAreaId;

    @NotNull
    private Long primarySocietyId;

    @Size(max = 120)
    private String building;

    @NotNull
    private Long serviceAreaId;

    @NotEmpty
    private List<@NotNull Long> serviceSocietyIds;

    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }
    public String getAlternateContact() { return alternateContact; }
    public void setAlternateContact(String alternateContact) { this.alternateContact = alternateContact; }
    public String getKitchenName() { return kitchenName; }
    public void setKitchenName(String kitchenName) { this.kitchenName = kitchenName; }
    public String getKitchenSlug() { return kitchenSlug; }
    public void setKitchenSlug(String kitchenSlug) { this.kitchenSlug = kitchenSlug; }
    public String getSpeciality() { return speciality; }
    public void setSpeciality(String speciality) { this.speciality = speciality; }
    public String getSellerCategory() { return sellerCategory; }
    public void setSellerCategory(String sellerCategory) { this.sellerCategory = sellerCategory; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getInstagramLink() { return instagramLink; }
    public void setInstagramLink(String instagramLink) { this.instagramLink = instagramLink; }
    public Long getPrimaryAreaId() { return primaryAreaId; }
    public void setPrimaryAreaId(Long primaryAreaId) { this.primaryAreaId = primaryAreaId; }
    public Long getPrimarySocietyId() { return primarySocietyId; }
    public void setPrimarySocietyId(Long primarySocietyId) { this.primarySocietyId = primarySocietyId; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public Long getServiceAreaId() { return serviceAreaId; }
    public void setServiceAreaId(Long serviceAreaId) { this.serviceAreaId = serviceAreaId; }
    public List<Long> getServiceSocietyIds() { return serviceSocietyIds; }
    public void setServiceSocietyIds(List<Long> serviceSocietyIds) { this.serviceSocietyIds = serviceSocietyIds; }
}
