package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServicePaymentTest {

    @Mock OrderRepository orderRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock AnalyticsService analyticsService;

    @InjectMocks OrderService orderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void updatePaymentPaidTransitionsOrderedToConfirmed() {
        User buyer = new User("Test Buyer", "9999999999", "A-101", UserRole.BUYER);
        buyer.setId(1L);
        Kitchen kitchen = new Kitchen("test-kitchen", "Test Kitchen", "desc", null, null);
        kitchen.setId(1L);
        Order order = new Order(buyer, kitchen);
        order.setId(100L);
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = orderService.updatePaymentStatus(100L, PaymentStatus.PAID, buyer);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void updatePaymentAlreadyConfirmedDoesNotChangeStatus() {
        User buyer = new User("Test Buyer", "9999999999", "A-101", UserRole.BUYER);
        buyer.setId(1L);
        Kitchen kitchen = new Kitchen("test-kitchen", "Test Kitchen", "desc", null, null);
        kitchen.setId(1L);
        Order order = new Order(buyer, kitchen);
        order.setId(101L);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);

        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = orderService.updatePaymentStatus(101L, PaymentStatus.PAID, buyer);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void updatePaymentDoesNotTransitionCancelledOrder() {
        User buyer = new User("Test Buyer", "9999999999", "A-101", UserRole.BUYER);
        buyer.setId(1L);
        Kitchen kitchen = new Kitchen("test-kitchen", "Test Kitchen", "desc", null, null);
        kitchen.setId(1L);
        Order order = new Order(buyer, kitchen);
        order.setId(102L);
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findById(102L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = orderService.updatePaymentStatus(102L, PaymentStatus.PAID, buyer);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void updatePaymentPendingDoesNotConfirmOrder() {
        User buyer = new User("Test Buyer", "9999999999", "A-101", UserRole.BUYER);
        buyer.setId(1L);
        Kitchen kitchen = new Kitchen("test-kitchen", "Test Kitchen", "desc", null, null);
        kitchen.setId(1L);
        Order order = new Order(buyer, kitchen);
        order.setId(103L);
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PAID);

        when(orderRepository.findById(103L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = orderService.updatePaymentStatus(103L, PaymentStatus.PENDING, buyer);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
    }
}
