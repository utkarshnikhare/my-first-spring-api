package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Screen 7B: Item-level drill-down detail for a seller.
 */
public class OrderItemDetailDto {

    private Long productId;
    private String productName;
    private String productImageUrl;
    private BigDecimal totalRevenue;
    private int totalPlates;
    private int paidCount;
    private int pendingCount;
    private int cancelledCount;
    private List<CustomerOrderRow> customers;

    public static class CustomerOrderRow {
        private Long orderId;
        private String orderNumber;
        private int quantity;
        private String buyerName;
        private String buyerFlat;
        private String society;
        private boolean paid;
        private boolean cancelled;
        private String remark;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getBuyerName() { return buyerName; }
        public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
        public String getBuyerFlat() { return buyerFlat; }
        public void setBuyerFlat(String buyerFlat) { this.buyerFlat = buyerFlat; }
        public String getSociety() { return society; }
        public void setSociety(String society) { this.society = society; }
        public boolean isPaid() { return paid; }
        public void setPaid(boolean paid) { this.paid = paid; }
        public boolean isCancelled() { return cancelled; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductImageUrl() { return productImageUrl; }
    public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public int getTotalPlates() { return totalPlates; }
    public void setTotalPlates(int totalPlates) { this.totalPlates = totalPlates; }
    public int getPaidCount() { return paidCount; }
    public void setPaidCount(int paidCount) { this.paidCount = paidCount; }
    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    public int getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }
    public List<CustomerOrderRow> getCustomers() { return customers; }
    public void setCustomers(List<CustomerOrderRow> customers) { this.customers = customers; }
}

