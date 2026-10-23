package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class BuyerRegistrationRequestDto {
    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    @Pattern(regexp = "\\d{10}", message = "Enter a 10-digit mobile number.")
    private String mobileNumber;

    @NotBlank
    @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters.")
    private String password;

    @NotBlank
    @Size(max = 80)
    private String flatHouseNumber;

    @NotBlank
    @Size(max = 120)
    private String building;

    @NotNull
    private Long areaId;
    @NotNull
    private Long societyId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFlatHouseNumber() { return flatHouseNumber; }
    public void setFlatHouseNumber(String flatHouseNumber) { this.flatHouseNumber = flatHouseNumber; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public Long getSocietyId() { return societyId; }
    public void setSocietyId(Long societyId) { this.societyId = societyId; }
}
