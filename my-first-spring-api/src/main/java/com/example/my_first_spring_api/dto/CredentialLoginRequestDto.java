package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CredentialLoginRequestDto {
    @NotBlank
    @Pattern(regexp = "\\d{10}", message = "Enter a 10-digit mobile number.")
    private String mobileNumber;

    @NotBlank
    @Size(max = 72, message = "Password is too long.")
    private String password;

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
