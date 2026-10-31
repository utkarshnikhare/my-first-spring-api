package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.OrderStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.my_first_spring_api.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDto {
    private Long id;
    private String orderNumber;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private String paymentMethod;
    private OrderStatus orderStatus;
    /**
     * Seller-recorded delivery completion. Always emitted, and emitted for the
     * BUYER from the SAME persisted Order row the seller wrote - there is no
     * buyer-specific delivery copy. A legacy order with no delivery history
     * reads back as NOT_DELIVERED.
     */
    private String deliveryStatus;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime orderTime;
    private LocalDateTime updatedAt;
    private KitchenSummary kitchen;
    private List<OrderItemDto> items;
    private BuyerSummary buyer;
    private String customInstructions;
    private LocalDateTime acknowledgedAt;
    private Long acknowledgedBySellerId;
    @JsonProperty("displayStatus")
    private String displayStatus;
    @JsonProperty("paymentFollowUpRequired")
    private Boolean paymentFollowUpRequired;

    public static class KitchenSummary {
        private Long id;
        private String name;
        private String displayName;
        private String imageUrl;
        private Double rating;
        private String society;
        private String upiId;
        private String acceptedPaymentMode;
        private String gpayIdentifierType;
        private String gpayIdentifier;

        public KitchenSummary() {}
        public KitchenSummary(Long id, String name, String displayName, String imageUrl, Double rating) {
            this.id = id; this.name = name; this.displayName = displayName;
            this.imageUrl = imageUrl; this.rating = rating;
        }
        public KitchenSummary(Long id, String name, String displayName, String imageUrl, Double rating, String society) {
            this.id = id; this.name = name; this.displayName = displayName;
            this.imageUrl = imageUrl; this.rating = rating; this.society = society;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public Double getRating() { return rating; }
        public void setRating(Double rating) { this.rating = rating; }
        public String getSociety() { return society; }
        public void setSociety(String society) { this.society = society; }
        public String getUpiId() { return upiId; }
        public void setUpiId(String upiId) { this.upiId = upiId; }
        public String getAcceptedPaymentMode() { return acceptedPaymentMode; }
        public void setAcceptedPaymentMode(String acceptedPaymentMode) { this.acceptedPaymentMode = acceptedPaymentMode; }
        public String getGpayIdentifierType() { return gpayIdentifierType; }
        public void setGpayIdentifierType(String gpayIdentifierType) { this.gpayIdentifierType = gpayIdentifierType; }
        public String getGpayIdentifier() { return gpayIdentifier; }
        public void setGpayIdentifier(String gpayIdentifier) { this.gpayIdentifier = gpayIdentifier; }
    }

    public static class BuyerSummary {
        private String name;
        private String mobileNumber;
        private String flatHouseNumber;
        private String society;
        private String building;

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
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public OrderStatus getOrderStatus() { return orderStatus; }
    public void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getOrderTime() { return orderTime; }
    public void setOrderTime(LocalDateTime orderTime) { this.orderTime = orderTime; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public KitchenSummary getKitchen() { return kitchen; }
    public void setKitchen(KitchenSummary kitchen) { this.kitchen = kitchen; }
    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }
    public BuyerSummary getBuyer() { return buyer; }
    public void setBuyer(BuyerSummary buyer) { this.buyer = buyer; }
    public String getCustomInstructions() { return customInstructions; }
    public void setCustomInstructions(String customInstructions) { this.customInstructions = customInstructions; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public Long getAcknowledgedBySellerId() { return acknowledgedBySellerId; }
    public void setAcknowledgedBySellerId(Long acknowledgedBySellerId) { this.acknowledgedBySellerId = acknowledgedBySellerId; }

    public String getDisplayStatus() {
        if (displayStatus != null && !displayStatus.isBlank()) return displayStatus;
        if (orderStatus == OrderStatus.CANCELLED) {
            return "Cancelled";
        }
        boolean isDelivered = "DELIVERED".equalsIgnoreCase(deliveryStatus) || orderStatus == OrderStatus.DELIVERED;
        if (isDelivered) {
            return paymentStatus == PaymentStatus.PAID ? "Delivered · Paid" : "Delivered · Payment pending";
        }
        if (paymentStatus == PaymentStatus.PAID) {
            return "Confirmed · Paid";
        } else if (paymentStatus == PaymentStatus.WILL_PAY_LATER) {
            return "Confirmed · Pay later";
        } else {
            return "Confirmed · Buyer says paid";
        }
    }

    public void setDisplayStatus(String displayStatus) {
        this.displayStatus = displayStatus;
    }

    public boolean isPaymentFollowUpRequired() {
        if (paymentFollowUpRequired != null) return paymentFollowUpRequired;
        return orderStatus == OrderStatus.CANCELLED && paymentStatus == PaymentStatus.PAID;
    }

    public void setPaymentFollowUpRequired(Boolean paymentFollowUpRequired) {
        this.paymentFollowUpRequired = paymentFollowUpRequired;
    }
}
