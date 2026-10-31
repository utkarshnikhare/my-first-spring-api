package com.example.my_first_spring_api.model;

public enum PaymentStatus {
    PENDING,
    UNPAID,
    AWAITING_SELLER_VERIFICATION,
    PAID,
    WILL_PAY_LATER;

    public boolean isUnsettled() {
        return this == PENDING || this == UNPAID
                || this == AWAITING_SELLER_VERIFICATION || this == WILL_PAY_LATER;
    }
}
