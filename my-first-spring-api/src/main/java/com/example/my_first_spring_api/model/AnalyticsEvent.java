package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Lightweight traffic/analytics event. Rows are written by AnalyticsService
 * at key interaction points (registration, login, menu views, orders, ...).
 * Dashboards are designed later — for now the data is simply collected and
 * summarised through the admin analytics API.
 */
@Entity
@Table(name = "analytics_events", indexes = {
        @Index(name = "idx_analytics_type", columnList = "event_type"),
        @Index(name = "idx_analytics_created", columnList = "created_at")
})
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. USER_REGISTERED, USER_LOGIN, SELLER_REGISTERED, ORDER_PLACED, MENU_VIEW, MARKETPLACE_VIEW */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_mobile")
    private String userMobile;

    @Column(name = "kitchen_id")
    private Long kitchenId;

    /** Optional free-form context, e.g. order number or kitchen name. */
    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public AnalyticsEvent() {}

    public AnalyticsEvent(String eventType, Long userId, String userMobile, Long kitchenId, String detail) {
        this.eventType = eventType;
        this.userId = userId;
        this.userMobile = userMobile;
        this.kitchenId = kitchenId;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserMobile() { return userMobile; }
    public void setUserMobile(String userMobile) { this.userMobile = userMobile; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
