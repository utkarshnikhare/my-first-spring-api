package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class OtpVerifyRequest {
    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
    private String mobileNumber;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{4}$", message = "Enter the 4-digit OTP")
    private String otpCode;

    private String name;
    private String flatHouseNumber;

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFlatHouseNumber() { return flatHouseNumber; }
    public void setFlatHouseNumber(String flatHouseNumber) { this.flatHouseNumber = flatHouseNumber; }
}
