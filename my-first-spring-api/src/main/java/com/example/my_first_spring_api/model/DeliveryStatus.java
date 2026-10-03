package com.example.my_first_spring_api.model;

/**
 * Seller-recorded delivery completion for an {@link Order} (V1 tracker).
 *
 * <p>This is deliberately a SEPARATE axis from {@link OrderStatus} and from
 * {@link PaymentStatus}: delivery is an operational event the seller records
 * when a hand-off actually happened, and it never implies anything about
 * money. All four combinations are valid - PAID + NOT_DELIVERED,
 * NOT_PAID + DELIVERED, PAID + DELIVERED and NOT_PAID + NOT_DELIVERED.</p>
 *
 * <p>Stored as a STRING enum on the common Order row, so Kitchen and Homemade
 * Product orders share one delivery implementation instead of each seller
 * category growing its own.</p>
 */
public enum DeliveryStatus {
    /** No delivery has been recorded by the seller. This is the initial state. */
    NOT_DELIVERED,
    /** The seller recorded that this order was handed over to the customer. */
    DELIVERED
}