package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public class UpdatePaymentStatusRequest {
    @NotNull(message = "Payment status is required")
    private PaymentStatus paymentStatus;

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
}
