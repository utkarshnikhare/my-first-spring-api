package com.example.my_first_spring_api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seller Home Dashboard (Screen 1) payload.
 * Top metrics + live offering cards + earnings preview.
 */
public class SellerDashboardDto {

    // Top metric cards
    private int viewsToday;
    private int followers;
    private int totalOrders;

    // Live offering cards
    private List<ProductDto> offerings;

    // Earnings preview
    private BigDecimal confirmedToday;
    private BigDecimal pending;
    private BigDecimal thisMonth;

    // Kitchen info
    private Long kitchenId;
    private String kitchenName;

    public SellerDashboardDto() {}

    public int getViewsToday() { return viewsToday; }
    public void setViewsToday(int viewsToday) { this.viewsToday = viewsToday; }
    public int getFollowers() { return followers; }
    public void setFollowers(int followers) { this.followers = followers; }
    public int getTotalOrders() { return totalOrders; }
    public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }
    public List<ProductDto> getOfferings() { return offerings; }
    public void setOfferings(List<ProductDto> offerings) { this.offerings = offerings; }
    public BigDecimal getConfirmedToday() { return confirmedToday; }
    public void setConfirmedToday(BigDecimal confirmedToday) { this.confirmedToday = confirmedToday; }
    public BigDecimal getPending() { return pending; }
    public void setPending(BigDecimal pending) { this.pending = pending; }
    public BigDecimal getThisMonth() { return thisMonth; }
    public void setThisMonth(BigDecimal thisMonth) { this.thisMonth = thisMonth; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getKitchenName() { return kitchenName; }
    public void setKitchenName(String kitchenName) { this.kitchenName = kitchenName; }
}

