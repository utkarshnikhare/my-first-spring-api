package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the forgot-password flow. The e-mail is the lookup key over
 * the (nullable) {@code User.email} column.
 */
public class ForgotPasswordRequestDto {

    @NotBlank(message = "E-mail is required.")
    @Email(message = "E-mail must be a valid address.")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
