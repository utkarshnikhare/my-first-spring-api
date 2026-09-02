package com.example.my_first_spring_api.dto;

import java.time.LocalDateTime;

public class EnquiryDto {
    private Long id;
    private Long kitchenId;
    private String kitchenName;
    private String kitchenImageUrl;
    private String message;
    /** WAITING_FOR_RESPONSE | SELLER_RESPONDED */
    private String status;
    private LocalDateTime createdAt;

    public EnquiryDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getKitchenName() { return kitchenName; }
    public void setKitchenName(String kitchenName) { this.kitchenName = kitchenName; }
    public String getKitchenImageUrl() { return kitchenImageUrl; }
    public void setKitchenImageUrl(String kitchenImageUrl) { this.kitchenImageUrl = kitchenImageUrl; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
