package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "mobile_number", unique = true)
    private String mobileNumber;

    @Column(name = "flat_house_number")
    private String flatHouseNumber;

    @Column(name = "society")
    private String society;

    @Column(name = "building")
    private String building;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /** Approval lifecycle for SELLER accounts (null = legacy seller, treated as APPROVED). */
    @Enumerated(EnumType.STRING)
    @Column(name = "seller_approval_status")
    private SellerApprovalStatus sellerApprovalStatus;

    @Column(name = "seller_status_reason", columnDefinition = "TEXT")
    private String sellerStatusReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

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

    public User() {}

    public User(String name, String mobileNumber, String flatHouseNumber, UserRole role) {
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.flatHouseNumber = flatHouseNumber;
        this.role = role;
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
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public SellerApprovalStatus getSellerApprovalStatus() { return sellerApprovalStatus; }
    public void setSellerApprovalStatus(SellerApprovalStatus sellerApprovalStatus) { this.sellerApprovalStatus = sellerApprovalStatus; }
    public String getSellerStatusReason() { return sellerStatusReason; }
    public void setSellerStatusReason(String sellerStatusReason) { this.sellerStatusReason = sellerStatusReason; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    /**
     * Legacy sellers (created before the approval workflow existed) have a null
     * status and are treated as APPROVED so existing behaviour is preserved.
     */
    public boolean isApprovedSeller() {
        return sellerApprovalStatus == null || sellerApprovalStatus == SellerApprovalStatus.APPROVED;
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
