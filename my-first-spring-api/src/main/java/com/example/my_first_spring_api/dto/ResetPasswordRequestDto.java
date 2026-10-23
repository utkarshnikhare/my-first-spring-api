package com.example.my_first_spring_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for the reset-password flow. {@code token} comes from the reset
 * link; {@code newPassword} must be a BCrypt-compatible password (<= 72 UTF-8
 * bytes).
 */
public class ResetPasswordRequestDto {

    @NotBlank(message = "Reset token is required.")
    private String token;

    @NotBlank(message = "New password is required.")
    @Size(max = 72, message = "Password is too long.")
    private String newPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
