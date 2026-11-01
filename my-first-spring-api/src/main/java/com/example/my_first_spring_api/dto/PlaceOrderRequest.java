package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public class PlaceOrderRequest {
    @JsonAlias({"payment_status", "paymentStatus"})
    private PaymentStatus paymentStatus;

    @JsonAlias({"buyer_details", "buyerDetails"})
    private BuyerDetails buyerDetails;

    @JsonAlias({"custom_instructions", "customInstructions", "specialInstructions", "special_instructions", "notes", "note"})
    private String customInstructions;

    @JsonAlias({"delivery_address", "deliveryAddress", "address"})
    private String deliveryAddress;

    @JsonAlias({"society_id", "societyId"})
    private Long societyId;

    private String society;

    @JsonAlias({"buyer_phone", "buyerPhone", "phone", "mobile", "mobileNumber", "mobile_number"})
    private String buyerPhone;

    @JsonAlias({"buyer_name", "buyerName", "name"})
    private String buyerName;

    @JsonAlias({"buyer_id", "buyerId"})
    private Long buyerId;

    @JsonAlias({"payment_method", "paymentMethod"})
    private String paymentMethod;

    @JsonAlias({"kitchen_id", "kitchenId"})
    private Long kitchenId;

    private List<OrderItemRequest> items;

    public static class BuyerDetails {
        @JsonAlias({"name", "buyer_name", "buyerName"})
        private String name;

        @JsonAlias({"mobile_number", "mobileNumber", "phone", "buyer_phone", "buyerPhone"})
        private String mobileNumber;

        @JsonAlias({"flat_house_number", "flatHouseNumber", "flatNumber", "flat"})
        private String flatHouseNumber;

        @JsonAlias({"society", "society_name", "societyName"})
        private String society;

        @JsonAlias({"society_id", "societyId"})
        private Long societyId;

        @JsonAlias({"building", "wing", "tower"})
        private String building;

        @JsonAlias({"delivery_address", "deliveryAddress", "address"})
        private String deliveryAddress;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBuyerName() { return name; }
        public void setBuyerName(String buyerName) { this.name = buyerName; }
        public String getMobileNumber() { return mobileNumber; }
        public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
        public String getPhone() { return mobileNumber; }
        public void setPhone(String phone) { this.mobileNumber = phone; }
        public String getBuyerPhone() { return mobileNumber; }
        public void setBuyerPhone(String buyerPhone) { this.mobileNumber = buyerPhone; }
        public String getFlatHouseNumber() { return flatHouseNumber; }
        public void setFlatHouseNumber(String flatHouseNumber) { this.flatHouseNumber = flatHouseNumber; }
        public String getSociety() { return society; }
        public void setSociety(String society) { this.society = society; }
        public Long getSocietyId() { return societyId; }
        public void setSocietyId(Long societyId) { this.societyId = societyId; }
        public String getBuilding() { return building; }
        public void setBuilding(String building) { this.building = building; }
        public String getDeliveryAddress() { return deliveryAddress; }
        public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public BuyerDetails getBuyerDetails() { return buyerDetails; }
    public void setBuyerDetails(BuyerDetails buyerDetails) { this.buyerDetails = buyerDetails; }
    public String getCustomInstructions() { return customInstructions; }
    public void setCustomInstructions(String customInstructions) { this.customInstructions = customInstructions; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public Long getSocietyId() { return societyId; }
    public void setSocietyId(Long societyId) { this.societyId = societyId; }
    public String getSociety() { return society; }
    public void setSociety(String society) { this.society = society; }
    public String getBuyerPhone() { return buyerPhone; }
    public void setBuyerPhone(String buyerPhone) { this.buyerPhone = buyerPhone; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public List<OrderItemRequest> getItems() { return items; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }
}
