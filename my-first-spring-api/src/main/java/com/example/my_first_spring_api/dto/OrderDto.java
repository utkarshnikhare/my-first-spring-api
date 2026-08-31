package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDto {
    private Long id;
    private String orderNumber;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private OrderStatus orderStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private KitchenSummary kitchen;
    private List<OrderItemDto> items;
    private BuyerSummary buyer;
    private String customInstructions;

    public static class KitchenSummary {
        private Long id;
        private String name;
        private String displayName;
        private String imageUrl;
        private Double rating;

        public KitchenSummary() {}
        public KitchenSummary(Long id, String name, String displayName, String imageUrl, Double rating) {
            this.id = id; this.name = name; this.displayName = displayName;
            this.imageUrl = imageUrl; this.rating = rating;
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
    }

    public static class BuyerSummary {
        private String name;
        private String mobileNumber;
        private String flatHouseNumber;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMobileNumber() { return mobileNumber; }
        public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
        public String getFlatHouseNumber() { return flatHouseNumber; }
        public void setFlatHouseNumber(String flatHouseNumber) { this.flatHouseNumber = flatHouseNumber; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public OrderStatus getOrderStatus() { return orderStatus; }
    public void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
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
}
