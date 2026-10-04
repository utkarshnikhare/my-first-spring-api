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
    @Mock RetentionService retentionService;
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
        buyer.setSociety("Sunshine Society");
        buyer.setBuilding("Building A");
        buyer.setFlatHouseNumber("A-101");
        when(userRepository.findById(20L)).thenReturn(Optional.of(buyer));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });

        com.example.my_first_spring_api.dto.OrderItemRequest req = new com.example.my_first_spring_api.dto.OrderItemRequest();
        req.setProductId(1L);
        req.setQuantity(1);
        sessionMap.put("BUYER_USER", buyer.getId());

        orderService.createOrUpdateDraftOrder(1L, List.of(req), httpSession);
        sessionMap.put("BUYER_USER", buyer.getId());

        Order savedDraft = new Order();
        savedDraft.setId(100L);
        savedDraft.setBuyer(buyer);
        savedDraft.setKitchen(kitchen);
        savedDraft.setItems(List.of(new OrderItem(product, 1, BigDecimal.valueOf(20))));
        savedDraft.recalculateTotal();
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(savedDraft));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.placeOrder(PaymentStatus.PAID, null, null, httpSession);

        verify(notificationService, atLeastOnce()).sendNewOrderNotification(argThat(u -> u != null && u.getId().equals(10L)), any(), any());
    }

    @Test
    void placeOrderRollsTheOrderIntoTheDurableDailyAggregate() {
        User seller = new User("Seller", "9100000002", "A-101", UserRole.SELLER);
        seller.setId(11L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(2L);
        kitchen.setAvailableToday(true);
        when(kitchenRepository.findById(2L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Upma", "desc", BigDecimal.valueOf(50), null);
        product.setId(2L);
        product.setAvailableToday(true);
        product.setRemainingQuantity(100);
        product.setMaxQuantity(100);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        User buyer = new User("Buyer", "9876500002", "A-101", UserRole.BUYER);
        buyer.setId(21L);
        buyer.setSociety("Green Park");
        buyer.setBuilding("Building A");
        buyer.setFlatHouseNumber("A-101");
        when(userRepository.findById(21L)).thenReturn(Optional.of(buyer));

        Order savedDraft = new Order();
        savedDraft.setId(200L);
        savedDraft.setBuyer(buyer);
        savedDraft.setKitchen(kitchen);
        savedDraft.setItems(List.of(new OrderItem(product, 2, BigDecimal.valueOf(50))));
        savedDraft.recalculateTotal();
        when(orderRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(savedDraft));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        sessionMap.put(OrderService.DRAFT_ORDER_SESSION_KEY, 200L);
        sessionMap.put("BUYER_USER", buyer.getId());

        orderService.placeOrder(PaymentStatus.PAID, null, null, httpSession);

        // Handover 12: the rollup is fed at placement so aggregate analytics
        // outlive any future detailed-order purge. Regression guard - this call
        // was previously missing, leaving the rollup permanently empty.
        verify(retentionService, times(1)).recordOrderFact(argThat(o ->
                o != null && o.getId().equals(200L)
                        && o.getTotalAmount() != null
                        && o.getTotalAmount().compareTo(BigDecimal.valueOf(100)) == 0));
    }
}
