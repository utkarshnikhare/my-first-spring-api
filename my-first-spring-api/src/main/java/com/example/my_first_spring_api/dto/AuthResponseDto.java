package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.SellerApprovalStatus;

public class AuthResponseDto {
    private boolean authenticated;
    private String message;
    private Long userId;
    private String name;
    private String mobileNumber;
    private String flatHouseNumber;
    private String society;
    private String building;
    /**
     * The buyer's persisted area. Additive field with its own accessors so every
     * existing constructor call site stays source-compatible. The profile screen
     * restores the Area dropdown from here on reload.
     */
    private String area;
    private String role;
    private SellerApprovalStatus sellerApprovalStatus;

    public AuthResponseDto() {}

    public AuthResponseDto(boolean authenticated, String message, Long userId, String name,
                           String mobileNumber, String flatHouseNumber, String role) {
        this(authenticated, message, userId, name, mobileNumber, flatHouseNumber, role, null, null, null);
    }

    public AuthResponseDto(boolean authenticated, String message, Long userId, String name,
                           String mobileNumber, String flatHouseNumber, String role,
                           SellerApprovalStatus sellerApprovalStatus) {
        this(authenticated, message, userId, name, mobileNumber, flatHouseNumber, role,
                sellerApprovalStatus, null, null);
    }

    /**
     * Full constructor including the buyer's persisted society/building.
     * The client renders the profile form and the "Deliver to" summary from
     * this payload, so it MUST carry the real persisted values — otherwise the
     * UI falls back to a placeholder and saving the form would overwrite the
     * buyer's actual service area.
     */
    public AuthResponseDto(boolean authenticated, String message, Long userId, String name,
                           String mobileNumber, String flatHouseNumber, String role,
                           SellerApprovalStatus sellerApprovalStatus, String society, String building) {
        this.authenticated = authenticated;
        this.message = message;
        this.userId = userId;
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.flatHouseNumber = flatHouseNumber;
        this.role = role;
        this.sellerApprovalStatus = sellerApprovalStatus;
        this.society = society;
        this.building = building;
    }

    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getFlatHouseNumber() { return flatHouseNumber; }
    public void setFlatHouseNumber(String flatHouseNumber) { this.flatHouseNumber = flatHouseNumber; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getSociety() { return society; }
    public void setSociety(String society) { this.society = society; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public SellerApprovalStatus getSellerApprovalStatus() { return sellerApprovalStatus; }
    public void setSellerApprovalStatus(SellerApprovalStatus sellerApprovalStatus) { this.sellerApprovalStatus = sellerApprovalStatus; }
}
