package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily rollup of marketplace commercial facts (Admin handover section 12).
 *
 * <p>The handover requires two retention levels: detailed operational order
 * rows may be purged after a short configurable window, but the aggregated
 * analytics (daily counts and recorded order value, by seller / Area / Society)
 * must survive that purge so trend and monetisation analysis stays possible.
 *
 * <p>One row per {@code (day, seller, area, society, category)}. The grain is
 * intentionally the same set of dimensions the Admin Analytics screen breaks
 * down by, so an aggregate can be re-derived without inventing a second
 * reporting model.
 *
 * <p>The {@code (day, seller, area, society, category)} grain is enforced in
 * {@code RetentionService.recordOrderFact}, which upserts the existing bucket
 * rather than appending a second row for the same grain.
 *
 * <p>This entity holds no buyer-identifying data.
 */
@Entity
@Table(name = "order_daily_aggregates", indexes = {
        // "day" is a reserved word in H2 2.x, so the COLUMN is agg_day even
        // though the JPQL field name stays `day`.
        @Index(name = "idx_agg_day", columnList = "agg_day"),
        @Index(name = "idx_agg_seller", columnList = "seller_id")
})
public class OrderDailyAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agg_day", nullable = false)
    private LocalDate day;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "seller_name")
    private String sellerName;

    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "area_name")
    private String areaName;

    @Column(name = "society_id")
    private Long societyId;

    @Column(name = "society_name")
    private String societyName;

    /** KITCHEN or HOMEMADE_PRODUCTS; null means the rollup spans every category. */
    @Column(name = "category")
    private String category;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "recorded_order_value", precision = 14, scale = 2, nullable = false)
    private BigDecimal recordedOrderValue = BigDecimal.ZERO;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = java.time.LocalDateTime.now();
    }

    public OrderDailyAggregate() {}

    public OrderDailyAggregate(LocalDate day, Long sellerId, String sellerName,
                               Long areaId, String areaName,
                               Long societyId, String societyName,
                               String category, int orderCount, BigDecimal recordedOrderValue) {
        this.day = day;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.areaId = areaId;
        this.areaName = areaName;
        this.societyId = societyId;
        this.societyName = societyName;
        this.category = category;
        this.orderCount = orderCount;
        this.recordedOrderValue = recordedOrderValue;
    }

    public Long getId() { return id; }
    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public Long getSocietyId() { return societyId; }
    public void setSocietyId(Long societyId) { this.societyId = societyId; }
    public String getSocietyName() { return societyName; }
    public void setSocietyName(String societyName) { this.societyName = societyName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
    public BigDecimal getRecordedOrderValue() { return recordedOrderValue; }
    public void setRecordedOrderValue(BigDecimal v) { this.recordedOrderValue = v; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
}