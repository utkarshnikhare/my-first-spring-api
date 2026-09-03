package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Screen 8: Earnings breakdown for the seller.
 */
public class SellerEarningsDto {

    private BigDecimal confirmedToday;
    private BigDecimal pending;
    private BigDecimal thisMonth;
    private List<ItemEarning> items;

    public static class ItemEarning {
        private Long productId;
        private String productName;
        private String imageUrl;
        private int totalOrders;
        private BigDecimal confirmedRevenue;
        private BigDecimal pendingRevenue;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public int getTotalOrders() { return totalOrders; }
        public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }
        public BigDecimal getConfirmedRevenue() { return confirmedRevenue; }
        public void setConfirmedRevenue(BigDecimal confirmedRevenue) { this.confirmedRevenue = confirmedRevenue; }
        public BigDecimal getPendingRevenue() { return pendingRevenue; }
        public void setPendingRevenue(BigDecimal pendingRevenue) { this.pendingRevenue = pendingRevenue; }
    }

    public BigDecimal getConfirmedToday() { return confirmedToday; }
    public void setConfirmedToday(BigDecimal confirmedToday) { this.confirmedToday = confirmedToday; }
    public BigDecimal getPending() { return pending; }
    public void setPending(BigDecimal pending) { this.pending = pending; }
    public BigDecimal getThisMonth() { return thisMonth; }
    public void setThisMonth(BigDecimal thisMonth) { this.thisMonth = thisMonth; }
    public List<ItemEarning> getItems() { return items; }
    public void setItems(List<ItemEarning> items) { this.items = items; }
}

