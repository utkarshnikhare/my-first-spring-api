package com.example.my_first_spring_api.dto;

public class BuyerProfileDto {
    private Long id;
    private String name;
    private String mobileNumber;
    private String flatHouseNumber;
    private String society;
    private String building;

    public BuyerProfileDto() {}

    public BuyerProfileDto(Long id, String name, String mobileNumber, String flatHouseNumber) {
        this.id = id;
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.flatHouseNumber = flatHouseNumber;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getFlatHouseNumber() { return flatHouseNumber; }
    public void setFlatHouseNumber(String flatHouseNumber) { this.flatHouseNumber = flatHouseNumber; }
    public String getSociety() { return society; }
    public void setSociety(String society) { this.society = society; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
}
