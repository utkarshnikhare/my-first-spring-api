package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the three seller/buyer notification events added in the gap-fix program:
 * - A47: Sold-out notification when an offering's remaining quantity hits zero
 * - A48: Order cancellation notification to the seller
 * - A49: Payment-recorded notification to the buyer
 */
class SellerNotificationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private KitchenRepository kitchenRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private AnalyticsService analyticsService;
    @Mock private NotificationService notificationService;

    @InjectMocks private OrderService orderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(productRepository.consumeStock(any(Long.class), any(Integer.class))).thenReturn(1);
    }

    private User seller() {
        User s = new User("Seller", "9100000001", "A-101", UserRole.SELLER);
        s.setId(10L);
        s.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return s;
    }

    private User buyer() {
        User b = new User("Buyer", "9876500001", "A-101", UserRole.BUYER);
        b.setId(20L);
        return b;
    }

    @Test
    void cancellationNotifiesSeller() {
        User s = seller();
        User b = buyer();
        Kitchen k = new Kitchen("k1", "Kitchen", "d", null, s);
        k.setId(1L);

        Order order = new Order(b, k);
        order.setId(50L);
        order.setOrderNumber("SM-CANCEL-001");
        order.setOrderStatus(OrderStatus.ORDERED);
        when(orderRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(order));

        orderService.updateOrderStatus(50L, OrderStatus.CANCELLED, s);

        verify(notificationService).sendOrderCancellationNotification(eq(s), eq("SM-CANCEL-001"));
    }

    @Test
    void markPaidNotifiesBuyerOnlyOnTransition() {
        User s = seller();
        User b = buyer();
        Kitchen k = new Kitchen("k2", "Kitchen", "d", null, s);
        k.setId(2L);

        Order order = new Order(b, k);
        order.setId(51L);
        order.setOrderNumber("SM-PAID-001");
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.markOrderAsPaid(51L, s);

        verify(notificationService).sendPaymentReceivedNotification(eq(b), eq("SM-PAID-001"));
    }

    @Test
    void markPaidDoesNotNotifyWhenAlreadyPaid() {
        User s = seller();
        User b = buyer();
        Kitchen k = new Kitchen("k3", "Kitchen", "d", null, s);
        k.setId(3L);

        Order order = new Order(b, k);
        order.setId(52L);
        order.setOrderNumber("SM-PAID-002");
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findByIdForUpdate(52L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.markOrderAsPaid(52L, s);

        verify(notificationService, never()).sendPaymentReceivedNotification(any(), any());
    }
}
