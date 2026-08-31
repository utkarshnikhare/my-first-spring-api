package com.example.my_first_spring_api.dto;

public class AuthResponseDto {
    private boolean authenticated;
    private String message;
    private Long userId;
    private String name;
    private String mobileNumber;
    private String flatHouseNumber;
    private String role;

    public AuthResponseDto() {}

    public AuthResponseDto(boolean authenticated, String message, Long userId, String name,
                           String mobileNumber, String flatHouseNumber, String role) {
        this.authenticated = authenticated;
        this.message = message;
        this.userId = userId;
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.flatHouseNumber = flatHouseNumber;
        this.role = role;
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
}
