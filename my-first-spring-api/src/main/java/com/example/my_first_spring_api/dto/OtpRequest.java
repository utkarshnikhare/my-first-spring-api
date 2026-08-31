package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class OtpRequest {
    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
    private String mobileNumber;

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
}
