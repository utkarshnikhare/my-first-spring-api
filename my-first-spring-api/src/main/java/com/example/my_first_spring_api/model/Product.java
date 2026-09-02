package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_id", nullable = false)
    private Kitchen kitchen;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "price_unit")
    private String priceUnit;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "available_today")
    private Boolean availableToday = true;

    @Column(name = "available_date")
    private LocalDate availableDate;

    @Column(name = "order_window_start")
    private String orderWindowStart;

    @Column(name = "order_window_end")
    private String orderWindowEnd;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "remaining_quantity")
    private Integer remainingQuantity;

    @Column
    private Double rating = 0.0;

    @Column(name = "is_preorder")
    private Boolean isPreorder = false;

    /** Food category used for discovery (BREAKFAST/LUNCH/DINNER/SNACKS/SPECIAL). */
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private Category category;

    /**
     * OFFERING-LEVEL order cutoff (HH:mm, e.g. "11:30"). Cutoffs NEVER belong to
     * a kitchen as a whole — each offering owns its own strict deadline and it is
     * only revealed inside offering details / the ordering flow.
     */
    @Column(name = "cutoff_time")
    private String cutoffTime;

    /** Fulfillment / ready-by time text (e.g. "4:00 PM today"). */
    @Column(name = "ready_by_time")
    private String readyByTime;

    /** FIXED (one availability date) or FLEXIBLE (buyer picks date + slot). */
    @Enumerated(EnumType.STRING)
    @Column(name = "preorder_type")
    private PreorderType preorderType;

    /** Last orderable availability date for FLEXIBLE pre-orders (inclusive). */
    @Column(name = "available_until_date")
    private LocalDate availableUntilDate;

    /** Comma-separated time slots for FLEXIBLE pre-orders (e.g. "1:00 PM,4:00 PM,8:00 PM"). */
    @Column(name = "time_slots")
    private String timeSlots;

    /** Units already booked — drives the live demand progress bar ("18 / 50 booked"). */
    @Column(name = "booked_quantity")
    private Integer bookedQuantity = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Product() {}

    public Product(Kitchen kitchen, String name, String description, BigDecimal price, String imageUrl) {
        this.kitchen = kitchen;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Kitchen getKitchen() { return kitchen; }
    public void setKitchen(Kitchen kitchen) { this.kitchen = kitchen; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getPriceUnit() { return priceUnit; }
    public void setPriceUnit(String priceUnit) { this.priceUnit = priceUnit; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Boolean getAvailableToday() { return availableToday; }
    public void setAvailableToday(Boolean availableToday) { this.availableToday = availableToday; }
    public LocalDate getAvailableDate() { return availableDate; }
    public void setAvailableDate(LocalDate availableDate) { this.availableDate = availableDate; }
    public String getOrderWindowStart() { return orderWindowStart; }
    public void setOrderWindowStart(String orderWindowStart) { this.orderWindowStart = orderWindowStart; }
    public String getOrderWindowEnd() { return orderWindowEnd; }
    public void setOrderWindowEnd(String orderWindowEnd) { this.orderWindowEnd = orderWindowEnd; }
    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }
    public Integer getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Boolean getIsPreorder() { return isPreorder; }
    public void setIsPreorder(Boolean isPreorder) { this.isPreorder = isPreorder; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getCutoffTime() { return cutoffTime; }
    public void setCutoffTime(String cutoffTime) { this.cutoffTime = cutoffTime; }
    public String getReadyByTime() { return readyByTime; }
    public void setReadyByTime(String readyByTime) { this.readyByTime = readyByTime; }
    public PreorderType getPreorderType() { return preorderType; }
    public void setPreorderType(PreorderType preorderType) { this.preorderType = preorderType; }
    public LocalDate getAvailableUntilDate() { return availableUntilDate; }
    public void setAvailableUntilDate(LocalDate availableUntilDate) { this.availableUntilDate = availableUntilDate; }
    public String getTimeSlots() { return timeSlots; }
    public void setTimeSlots(String timeSlots) { this.timeSlots = timeSlots; }
    public Integer getBookedQuantity() { return bookedQuantity; }
    public void setBookedQuantity(Integer bookedQuantity) { this.bookedQuantity = bookedQuantity; }

    /** Sold out when stock tracking is on and nothing remains. */
    @Transient
    public boolean isSoldOut() {
        return remainingQuantity != null && remainingQuantity <= 0;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
