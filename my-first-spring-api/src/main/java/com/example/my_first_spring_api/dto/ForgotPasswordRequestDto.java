package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for the forgot-password endpoint, which now serves two flows:
 *
 * <ul>
 *   <li>{@code mobileNumber} - manual (Admin-mediated) recovery V1. Accepts a
 *       10-digit mobile "login ID"; creates a password reset request reviewed by
 *       a Super Admin. The answer is always neutral.</li>
 *   <li>{@code email} - the legacy automated single-use reset link, keyed over
 *       the (nullable) {@code User.email} column.</li>
 * </ul>
 *
 * <p>At least one of the two must be supplied; the controller rejects a body
 * where both are absent.</p>
 */
public class ForgotPasswordRequestDto {

    @Email(message = "E-mail must be a valid address.")
    private String email;

    @Pattern(regexp = "\\d{10}", message = "Enter a 10-digit mobile number.")
    private String mobileNumber;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }
}
