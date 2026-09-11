package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceValidationTest {

    @Mock OrderRepository orderRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock AnalyticsService analyticsService;
    @Mock NotificationService notificationService;
    @Mock HttpSession httpSession;

    @InjectMocks OrderService orderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(httpSession.getAttribute(any(String.class))).thenReturn(null);
    }

    private User approvedSeller() {
        User seller = new User("Seller", "9100000001", "A-101", UserRole.SELLER);
        seller.setId(10L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return seller;
    }

    private Kitchen approvedKitchen() {
        User seller = approvedSeller();
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        return kitchen;
    }

    @Test
    void zeroQuantityRejected() {
        Kitchen kitchen = approvedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), null);
        product.setId(1L);
        product.setAvailableToday(true);
        product.setRemainingQuantity(10);
        product.setMaxQuantity(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> {
            OrderItemRequest req = new OrderItemRequest();
            req.setProductId(1L);
            req.setQuantity(0);
            orderService.createOrUpdateDraftOrder(1L, List.of(req), httpSession);
        });
    }

    @Test
    void negativeQuantityRejected() {
        Kitchen kitchen = approvedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), null);
        product.setId(1L);
        product.setAvailableToday(true);
        product.setRemainingQuantity(10);
        product.setMaxQuantity(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> {
            OrderItemRequest req = new OrderItemRequest();
            req.setProductId(1L);
            req.setQuantity(-1);
            orderService.createOrUpdateDraftOrder(1L, List.of(req), httpSession);
        });
    }

    @Test
    void quantityAboveRemainingRejected() {
        Kitchen kitchen = approvedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), null);
        product.setId(1L);
        product.setAvailableToday(true);
        product.setRemainingQuantity(5);
        product.setMaxQuantity(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> {
            OrderItemRequest req = new OrderItemRequest();
            req.setProductId(1L);
            req.setQuantity(6);
            orderService.createOrUpdateDraftOrder(1L, List.of(req), httpSession);
        });
    }

    @Test
    void invalidProductIdReturns404() {
        Kitchen kitchen = approvedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> {
            OrderItemRequest req = new OrderItemRequest();
            req.setProductId(999L);
            req.setQuantity(1);
            orderService.createOrUpdateDraftOrder(1L, List.of(req), httpSession);
        });
    }

    @Test
    void switchingKitchenClearsStaleDraftAndCreatesNewOne() {
        User seller = approvedSeller();
        Kitchen kitchen1 = approvedKitchen();
        kitchen1.setId(1L);
        Kitchen kitchen2 = new Kitchen("k2", "Kitchen 2", "d", null, seller);
        kitchen2.setId(2L);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen1));
        when(kitchenRepository.findById(2L)).thenReturn(Optional.of(kitchen2));

        Product product1 = new Product(kitchen1, "Poha", "desc", BigDecimal.valueOf(40), null);
        product1.setId(1L);
        product1.setAvailableToday(true);
        product1.setRemainingQuantity(10);
        product1.setMaxQuantity(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product1));

        Product product2 = new Product(kitchen2, "Dosa", "desc", BigDecimal.valueOf(50), null);
        product2.setId(2L);
        product2.setAvailableToday(true);
        product2.setRemainingQuantity(10);
        product2.setMaxQuantity(10);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product2));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderItemRequest req1 = new OrderItemRequest();
        req1.setProductId(1L);
        req1.setQuantity(1);
        OrderDto draft1 = orderService.createOrUpdateDraftOrder(1L, List.of(req1), httpSession);
        assertThat(draft1.getKitchen().getId()).isEqualTo(1L);
        assertThat(draft1.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(40));

        when(httpSession.getAttribute(OrderService.DRAFT_ORDER_SESSION_KEY)).thenReturn(100L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(new Order() {{
            setId(100L);
            setKitchen(kitchen1);
            setBuyer(new User("Buyer", "9876543210", "A-101", UserRole.BUYER));
        }}));

        OrderItemRequest req2 = new OrderItemRequest();
        req2.setProductId(2L);
        req2.setQuantity(1);
        OrderDto draft2 = orderService.createOrUpdateDraftOrder(2L, List.of(req2), httpSession);
        assertThat(draft2.getKitchen().getId()).isEqualTo(2L);
        assertThat(draft2.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(50));
        verify(orderRepository).delete(any(Order.class));
    }
}
