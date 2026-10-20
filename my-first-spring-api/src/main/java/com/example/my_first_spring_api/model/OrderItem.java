package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Legacy / database column synchronization with Supabase PostgreSQL schema */
    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "product_name")
    private String productName;

    /** Buyer-chosen availability date for pre-order items (null = today). */
    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    /** Buyer-chosen time slot for FLEXIBLE pre-order items (e.g. "4:00 PM"). */
    @Column(name = "scheduled_slot")
    private String scheduledSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occurrence_id")
    private Occurrence occurrence;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        syncFields();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        syncFields();
    }

    public void syncFields() {
        if (this.unitPrice == null) {
            this.unitPrice = this.price != null ? this.price : BigDecimal.ZERO;
        }
        if (this.price == null && this.unitPrice != null) {
            this.price = this.unitPrice;
        }
        if (this.productName == null && this.product != null) {
            this.productName = this.product.getName();
        }
    }

    public OrderItem() {}

    public OrderItem(Product product, Integer quantity, BigDecimal price) {
        this.product = product;
        this.quantity = quantity;
        this.price = price;
        this.unitPrice = price;
        this.productName = product != null ? product.getName() : null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) {
        this.product = product;
        if (product != null && this.productName == null) {
            this.productName = product.getName();
        }
    }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) {
        this.price = price;
        if (this.unitPrice == null) {
            this.unitPrice = price;
        }
    }
    public BigDecimal getUnitPrice() { return unitPrice != null ? unitPrice : price; }
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        if (this.price == null) {
            this.price = unitPrice;
        }
    }
    public String getProductName() { return productName != null ? productName : (product != null ? product.getName() : null); }
    public void setProductName(String productName) { this.productName = productName; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public String getScheduledSlot() { return scheduledSlot; }
    public void setScheduledSlot(String scheduledSlot) { this.scheduledSlot = scheduledSlot; }
    public Occurrence getOccurrence() { return occurrence; }
    public void setOccurrence(Occurrence occurrence) { this.occurrence = occurrence; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
