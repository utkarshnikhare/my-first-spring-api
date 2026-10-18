package com.example.my_first_spring_api.dto;

public class SellerProfileDto {
    private Long id;
    private String name;
    private String mobileNumber;
    private String whatsappNumber;
    private String email;
    private String alternateContact;
    private String area;
    private String society;
    private String building;
    private String flatHouseNumber;
    private String sellerCategory;
    private String sellerApprovalStatus;

    public SellerProfileDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAlternateContact() { return alternateContact; }
    public void setAlternateContact(String alternateContact) { this.alternateContact = alternateContact; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getSociety() { return society; }
    public void setSociety(String society) { this.society = society; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getFlatHouseNumber() { return flatHouseNumber; }
    public void setFlatHouseNumber(String flatHouseNumber) { this.flatHouseNumber = flatHouseNumber; }

    public String getSellerCategory() { return sellerCategory; }
    public void setSellerCategory(String sellerCategory) { this.sellerCategory = sellerCategory; }

    public String getSellerApprovalStatus() { return sellerApprovalStatus; }
    public void setSellerApprovalStatus(String sellerApprovalStatus) { this.sellerApprovalStatus = sellerApprovalStatus; }
}
