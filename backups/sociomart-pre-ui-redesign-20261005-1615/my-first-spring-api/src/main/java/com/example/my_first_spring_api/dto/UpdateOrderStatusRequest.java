package com.example.my_first_spring_api.dto;

import com.example.my_first_spring_api.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateOrderStatusRequest {
    @NotNull(message = "Order status is required")
    private OrderStatus orderStatus;

    public OrderStatus getOrderStatus() { return orderStatus; }
    public void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }
}
