package com.example.my_first_spring_api.dto;

import java.time.LocalDateTime;

public class EnquiryDto {
    private Long id;
    private Long kitchenId;
    private String kitchenName;
    private String kitchenImageUrl;
    private String message;
    /** NEW | CONTACTED | CLOSED */
    private String status;
    private String preferredDate;
    private String quantity;
    private String referenceImageUrl;
    private LocalDateTime acknowledgedAt;
    private Long acknowledgedBySellerId;
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
    public String getPreferredDate() { return preferredDate; }
    public void setPreferredDate(String preferredDate) { this.preferredDate = preferredDate; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getReferenceImageUrl() { return referenceImageUrl; }
    public void setReferenceImageUrl(String referenceImageUrl) { this.referenceImageUrl = referenceImageUrl; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public Long getAcknowledgedBySellerId() { return acknowledgedBySellerId; }
    public void setAcknowledgedBySellerId(Long acknowledgedBySellerId) { this.acknowledgedBySellerId = acknowledgedBySellerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
