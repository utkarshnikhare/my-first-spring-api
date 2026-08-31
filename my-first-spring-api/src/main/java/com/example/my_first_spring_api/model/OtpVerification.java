package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_verifications")
public class OtpVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "otp_code", nullable = false)
    private String otpCode;

    @Column(name = "expiry_time", nullable = false)
    private LocalDateTime expiryTime;

    @Column(nullable = false)
    private Boolean verified = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public OtpVerification() {}

    public OtpVerification(String mobileNumber, String otpCode, LocalDateTime expiryTime) {
        this.mobileNumber = mobileNumber;
        this.otpCode = otpCode;
        this.expiryTime = expiryTime;
        this.verified = false;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public LocalDateTime getExpiryTime() { return expiryTime; }
    public void setExpiryTime(LocalDateTime expiryTime) { this.expiryTime = expiryTime; }
    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
