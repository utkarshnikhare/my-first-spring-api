package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.PaymentStatus;

public class PlaceOrderRequest {
    private PaymentStatus paymentStatus;
    private BuyerDetails buyerDetails;
    private String customInstructions;

    public static class BuyerDetails {
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

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public BuyerDetails getBuyerDetails() { return buyerDetails; }
    public void setBuyerDetails(BuyerDetails buyerDetails) { this.buyerDetails = buyerDetails; }
    public String getCustomInstructions() { return customInstructions; }
    public void setCustomInstructions(String customInstructions) { this.customInstructions = customInstructions; }
}
