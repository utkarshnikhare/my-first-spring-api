package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Screen 7A: Daily aggregated order summary for a seller's kitchen.
 */
public class SellerOrderSummaryDto {

    private int totalOrderCount;
    private int paidCount;
    private int pendingCount;
    private int cancelledCount;
    private BigDecimal totalRevenue;
    private List<ProductOrderAggregate> products;

    public static class ProductOrderAggregate {
        private Long productId;
        private String productName;
        private String imageUrl;
        private int totalOrders;
        private int totalPlates;
        private BigDecimal revenue;
        private int paidCount;
        private int pendingCount;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public int getTotalOrders() { return totalOrders; }
        public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }
        public int getTotalPlates() { return totalPlates; }
        public void setTotalPlates(int totalPlates) { this.totalPlates = totalPlates; }
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        public int getPaidCount() { return paidCount; }
        public void setPaidCount(int paidCount) { this.paidCount = paidCount; }
        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    }

    public int getTotalOrderCount() { return totalOrderCount; }
    public void setTotalOrderCount(int totalOrderCount) { this.totalOrderCount = totalOrderCount; }
    public int getPaidCount() { return paidCount; }
    public void setPaidCount(int paidCount) { this.paidCount = paidCount; }
    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    public int getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public List<ProductOrderAggregate> getProducts() { return products; }
    public void setProducts(List<ProductOrderAggregate> products) { this.products = products; }
}

