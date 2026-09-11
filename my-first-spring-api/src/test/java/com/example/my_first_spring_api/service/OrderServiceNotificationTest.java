package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceNotificationTest {

    @Mock OrderRepository orderRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock AnalyticsService analyticsService;
    @Mock NotificationService notificationService;
    @Mock HttpSession httpSession;

    @InjectMocks OrderService orderService;

    private final Map<String, Object> sessionMap = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(httpSession.getAttribute(any(String.class))).thenAnswer(inv -> sessionMap.get(inv.getArgument(0)));
        doAnswer(inv -> {
            sessionMap.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(httpSession).setAttribute(any(String.class), any());
        doAnswer(inv -> {
            sessionMap.remove(inv.getArgument(0));
            return null;
        }).when(httpSession).removeAttribute(any(String.class));
        when(productRepository.consumeStock(any(Long.class), any(Integer.class))).thenReturn(1);
    }

    @Test
    void placeOrderSendsNotificationToSeller() {
        User seller = new User("Seller", "9100000001", "A-101", UserRole.SELLER);
        seller.setId(10L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        kitchen.setAvailableToday(true);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(20), null);
        product.setId(1L);
        product.setAvailableToday(true);
        product.setRemainingQuantity(100);
        product.setMaxQuantity(100);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        User buyer = new User("Buyer", "9876500001", "A-101", UserRole.BUYER);
        buyer.setId(20L);
        when(userRepository.findById(20L)).thenReturn(Optional.of(buyer));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });

        com.example.my_first_spring_api.dto.OrderItemRequest req = new com.example.my_first_spring_api.dto.OrderItemRequest();
        req.setProductId(1L);
        req.setQuantity(1);

        orderService.createOrUpdateDraftOrder(1L, List.of(req), httpSession);
        sessionMap.put("BUYER_USER", buyer.getId());

        Order savedDraft = new Order();
        savedDraft.setId(100L);
        savedDraft.setBuyer(buyer);
        savedDraft.setKitchen(kitchen);
        savedDraft.setItems(List.of(new OrderItem(product, 1, BigDecimal.valueOf(20))));
        savedDraft.recalculateTotal();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(savedDraft));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.placeOrder(PaymentStatus.PAID, null, null, httpSession);

        verify(notificationService, atLeastOnce()).sendNewOrderNotification(argThat(u -> u != null && u.getId().equals(10L)), any(), any());
    }
}
