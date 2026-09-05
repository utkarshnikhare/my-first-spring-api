package com.example.my_first_spring_api.service;

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
}
