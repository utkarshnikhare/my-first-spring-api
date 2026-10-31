package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_id", nullable = false)
    private Kitchen kitchen;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "payment_configuration_snapshot", length = 20)
    private String paymentConfigurationSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus = OrderStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status = OrderStatus.PENDING;

    @Transient
    private String specialInstructions = "";

    @Transient
    private String deliveryAddress = "";

    /**
     * Seller-recorded delivery completion (V1 tracker). Deliberately independent
     * of {@link #orderStatus} and {@link #paymentStatus}: marking an order
     * delivered never marks it paid and never marks it paid to deliver it.
     *
     * <p>Nullable on purpose. Orders that existed before this feature have no
     * delivery history, and {@code ddl-auto=update} leaves the column NULL for
     * them. Every read goes through {@link #getEffectiveDeliveryStatus()} or
     * {@link #isDelivered()}, so a NULL legacy row behaves exactly like
     * NOT_DELIVERED without needing data repair, and {@link #deliveredAt} is
     * never fabricated for it.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status")
    private DeliveryStatus deliveryStatus = DeliveryStatus.NOT_DELIVERED;

    /**
     * Backend clock time of the NOT_DELIVERED -> DELIVERED transition.
     *
     * <p>Written once, on the real transition only. Re-running "Mark All
     * Delivered" must not overwrite it, so the original hand-off time survives
     * repeated bulk runs. Cleared when the seller unchecks the box.</p>
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /**
     * Who last changed the delivery flag. Reuses the existing User-backed audit
     * convention ({@link #acknowledgedBy}) rather than introducing a new audit
     * framework for one feature.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_updated_by")
    private User deliveryUpdatedBy;

    @Column(name = "custom_instructions", columnDefinition = "TEXT")
    private String customInstructions = "";

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Authoritative timestamp set by the server when an order is FINALIZED (placed),
     * not when the draft was first created. This is the canonical "orderTime" —
     * the moment the buyer confirmed and the order entered the system.
     * Set explicitly in OrderService.placeOrder; never populated during draft creation.
     */
    @Column(name = "order_time")
    private LocalDateTime orderTime;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void ensureNonNullFields() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.orderNumber == null || this.orderNumber.isBlank()) {
            this.orderNumber = "ORD-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 9000 + 1000);
        }
        if (this.paymentStatus == null) this.paymentStatus = PaymentStatus.PENDING;
        if (this.orderStatus == null) {
            this.orderStatus = (this.status != null) ? this.status : OrderStatus.DRAFT;
        }
        if (this.status == null) {
            this.status = (this.orderStatus != null) ? this.orderStatus : OrderStatus.PENDING;
        }
        if (this.customInstructions == null) this.customInstructions = "";
        if (this.specialInstructions == null) this.specialInstructions = this.customInstructions;
        if (this.deliveryAddress == null) {
            this.deliveryAddress = "";
            if (this.buyer != null) {
                String addr = (this.buyer.getFlatHouseNumber() != null ? this.buyer.getFlatHouseNumber() + ", " : "")
                        + (this.buyer.getBuilding() != null ? this.buyer.getBuilding() + ", " : "")
                        + (this.buyer.getSociety() != null ? this.buyer.getSociety() : "");
                if (!addr.isBlank()) this.deliveryAddress = addr;
            }
        }
    }

    protected void onCreate() {
        ensureNonNullFields();
    }

    protected void onUpdate() {
        ensureNonNullFields();
    }

    public Order() {
        this.orderStatus = OrderStatus.DRAFT;
        this.status = OrderStatus.PENDING;
        this.paymentStatus = PaymentStatus.PENDING;
        this.customInstructions = "";
        this.specialInstructions = "";
        this.deliveryAddress = "";
    }

    public Order(User buyer, Kitchen kitchen) {
        this.buyer = buyer;
        this.kitchen = kitchen;
        this.orderStatus = OrderStatus.DRAFT;
        this.status = OrderStatus.PENDING;
        this.paymentStatus = PaymentStatus.PENDING;
        this.customInstructions = "";
        this.specialInstructions = "";
        this.deliveryAddress = "";
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    public void recalculateTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem item : items) {
            BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            sum = sum.add(price.multiply(BigDecimal.valueOf(quantity)));
        }
        totalAmount = sum;
    }

    // ==================== DELIVERY COMPLETION (V1) ====================

    /**
     * The delivery state to treat this order as having, normalising a legacy
     * NULL column to NOT_DELIVERED. Every delivery read must go through this,
     * so pre-feature orders need no data repair and are never counted as
     * delivered.
     */
    public DeliveryStatus getEffectiveDeliveryStatus() {
        return deliveryStatus == null ? DeliveryStatus.NOT_DELIVERED : deliveryStatus;
    }

    /** True only for a genuinely DELIVERED order (NULL legacy -> false). */
    public boolean isDelivered() {
        return getEffectiveDeliveryStatus() == DeliveryStatus.DELIVERED;
    }

    /**
     * Whether this order takes part in delivery tracking at all.
     *
     * <p>DRAFT is an unplaced basket and CANCELLED is a terminal state, so
     * neither is a pending delivery. This is the single definition used by the
     * progress counters AND the bulk update, so the number the seller is asked
     * to confirm is exactly the number of rows the backend will change.</p>
     */
    public boolean isActiveForDelivery() {
        return orderStatus != OrderStatus.DRAFT && orderStatus != OrderStatus.CANCELLED;
    }

    /**
     * Records the seller's delivery flag on the common Order row.
     *
     * <p>Idempotent by construction: re-delivering an already-delivered order is
     * a no-op, so a duplicate request or a repeated "Mark All Delivered" can
     * never rewrite {@link #deliveredAt} or create a second record. Un-delivering
     * clears the timestamp so the row never claims a hand-off that did not
     * happen. Payment status, order status and totals are never touched here -
     * delivery and payment stay independent axes.</p>
     *
     * @param target    the state the seller asked for
     * @param actor     the acting seller, recorded for audit
     * @param timestamp the backend clock time of the transition
     * @return true when the persisted state actually changed
     */
    public boolean applyDeliveryStatus(DeliveryStatus target, User actor, LocalDateTime timestamp) {
        DeliveryStatus desired = target == null ? DeliveryStatus.NOT_DELIVERED : target;
        if (getEffectiveDeliveryStatus() == desired) return false; // no duplicate record, no clock rewrite
        deliveryStatus = desired;
        deliveryUpdatedBy = actor;
        deliveredAt = desired == DeliveryStatus.DELIVERED ? timestamp : null;
        return true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentConfigurationSnapshot() { return paymentConfigurationSnapshot; }
    public void setPaymentConfigurationSnapshot(String paymentConfigurationSnapshot) {
        this.paymentConfigurationSnapshot = paymentConfigurationSnapshot;
    }
    public User getBuyer() { return buyer; }
    public void setBuyer(User buyer) { this.buyer = buyer; }
    public Kitchen getKitchen() { return kitchen; }
    public void setKitchen(Kitchen kitchen) { this.kitchen = kitchen; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public PaymentStatus getPaymentStatus() { return paymentStatus != null ? paymentStatus : PaymentStatus.PENDING; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus != null ? paymentStatus : PaymentStatus.PENDING; }
    public void setPaymentStatus(String paymentStatus) {
        if (paymentStatus == null || paymentStatus.isBlank()) {
            this.paymentStatus = PaymentStatus.PENDING;
        } else {
            try {
                this.paymentStatus = PaymentStatus.valueOf(paymentStatus.trim().toUpperCase());
            } catch (Exception e) {
                this.paymentStatus = PaymentStatus.PENDING;
            }
        }
    }
    public OrderStatus getOrderStatus() { return orderStatus != null ? orderStatus : (status != null ? status : OrderStatus.DRAFT); }
    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus != null ? orderStatus : OrderStatus.DRAFT;
        this.status = this.orderStatus;
    }
    public OrderStatus getStatus() { return status != null ? status : (orderStatus != null ? orderStatus : OrderStatus.PENDING); }
    public void setStatus(OrderStatus status) {
        this.status = status != null ? status : OrderStatus.PENDING;
        this.orderStatus = this.status;
    }
    public String getCustomInstructions() { return customInstructions != null ? customInstructions : ""; }
    public void setCustomInstructions(String customInstructions) {
        this.customInstructions = customInstructions != null ? customInstructions : "";
        this.specialInstructions = this.customInstructions;
    }
    public String getSpecialInstructions() {
        return specialInstructions != null ? specialInstructions : (customInstructions != null ? customInstructions : "");
    }
    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions != null ? specialInstructions : "";
        this.customInstructions = this.specialInstructions;
    }
    public String getDeliveryAddress() {
        if (deliveryAddress != null && !deliveryAddress.isBlank()) {
            return deliveryAddress;
        }
        if (buyer != null) {
            String addr = (buyer.getFlatHouseNumber() != null ? buyer.getFlatHouseNumber() + ", " : "")
                    + (buyer.getBuilding() != null ? buyer.getBuilding() + ", " : "")
                    + (buyer.getSociety() != null ? buyer.getSociety() : "");
            return addr.trim();
        }
        return deliveryAddress != null ? deliveryAddress : "";
    }
    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress != null ? deliveryAddress : "";
    }
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod != null ? paymentMethod : "";
    }

    @Transient
    public String getSociety() {
        return buyer != null ? buyer.getSociety() : null;
    }
    @Transient
    public Long getSocietyId() {
        return (buyer != null && buyer.getSocietyRef() != null) ? buyer.getSocietyRef().getId() : null;
    }
    @Transient
    public String getBuyerName() {
        return buyer != null ? buyer.getName() : null;
    }
    @Transient
    public String getBuyerPhone() {
        return buyer != null ? buyer.getMobileNumber() : null;
    }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public LocalDateTime getRemindedAt() { return remindedAt; }
    public void setRemindedAt(LocalDateTime remindedAt) { this.remindedAt = remindedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public User getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(User acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getOrderTime() { return orderTime; }
    public void setOrderTime(LocalDateTime orderTime) { this.orderTime = orderTime; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public User getDeliveryUpdatedBy() { return deliveryUpdatedBy; }
    public void setDeliveryUpdatedBy(User deliveryUpdatedBy) { this.deliveryUpdatedBy = deliveryUpdatedBy; }
}
