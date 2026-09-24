package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderServicePaymentTest {
    @Mock OrderRepository orderRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock AnalyticsService analyticsService;
    @Mock NotificationService notificationService;
    @InjectMocks OrderService orderService;
    private User buyer;
    private Order order;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        buyer = new User("Buyer", "9999999999", "A-101", UserRole.BUYER);
        buyer.setId(1L);
        User seller = new User("Seller", "9100000009", "S-1", UserRole.SELLER);
        seller.setId(2L);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "", null, seller);
        kitchen.setId(3L);
        order = new Order(buyer, kitchen);
        order.setId(4L);
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findById(4L)).thenReturn(Optional.of(order));
    }

    @Test
    void buyerCannotMarkOwnOrderPaid() {
        assertThatThrownBy(() -> orderService.updatePaymentStatus(4L, PaymentStatus.PAID, buyer))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("seller");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void buyerCannotRevertPaidOrderToPending() {
        order.setPaymentStatus(PaymentStatus.PAID);
        assertThatThrownBy(() -> orderService.updatePaymentStatus(4L, PaymentStatus.PENDING, buyer))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void buyerCanSetDeferredPaymentWithoutChangingOrderStatus() {
        var result = orderService.updatePaymentStatus(4L, PaymentStatus.PENDING, buyer);
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelledOrderCannotBeChangedByBuyer() {
        order.setOrderStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orderService.updatePaymentStatus(4L, PaymentStatus.PAID, buyer))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void buyerLegacyWillPayLaterIsPersistedAsPending() {
        var result = orderService.updatePaymentStatus(4L, PaymentStatus.WILL_PAY_LATER, buyer);
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void alreadyPaidStatusIsIdempotentForBuyerEndpoint() {
        order.setPaymentStatus(PaymentStatus.PAID);
        var result = orderService.updatePaymentStatus(4L, PaymentStatus.PAID, buyer);
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderRepository, never()).save(any());
    }
}