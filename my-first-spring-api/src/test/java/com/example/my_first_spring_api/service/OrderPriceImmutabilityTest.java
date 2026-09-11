package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderPriceImmutabilityTest {

    @Mock OrderRepository orderRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock AnalyticsService analyticsService;
    @Mock NotificationService notificationService;
    @Mock HttpSession httpSession;

    @InjectMocks OrderService orderService;

    private final Map<String, Object> sessionMap = new HashMap<>();

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

    private User approvedSeller() {
        User seller = new User("Seller", "9100000001", "A-101", UserRole.SELLER);
        seller.setId(10L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return seller;
    }

    private Kitchen approvedKitchen(User seller) {
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        kitchen.setAvailableToday(true);
        return kitchen;
    }

    @Test
    void historicalOrderPriceRemainsUnchangedWhenProductPriceChanges() {
        User seller = approvedSeller();
        Kitchen kitchen = approvedKitchen(seller);
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

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(1L);
        req.setQuantity(100);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderDto draft = orderService.createOrUpdateDraftOrder(1L, List.of(req), httpSession);
        assertThat(draft.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        Order savedDraft = new Order();
        savedDraft.setId(100L);
        savedDraft.setBuyer(buyer);
        savedDraft.setKitchen(kitchen);
        savedDraft.setItems(List.of(new OrderItem(product, 100, BigDecimal.valueOf(20))));
        savedDraft.recalculateTotal();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(savedDraft));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sessionMap.put("BUYER_USER", buyer.getId());

        OrderDto placed = orderService.placeOrder(PaymentStatus.PAID, null, null, httpSession);
        assertThat(placed.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(placed.getItems()).hasSize(1);
        assertThat(placed.getItems().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(placed.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(savedDraft.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(savedDraft.getOrderTime()).isNotNull();
        assertThat(placed.getOrderTime()).isNotNull();

        product.setPrice(BigDecimal.valueOf(30));
        product.setRemainingQuantity(100);

        when(orderRepository.findByBuyerOrderByCreatedAtDesc(buyer)).thenReturn(List.of(savedDraft));
        Map<String, List<OrderDto>> myOrders = orderService.getMyOrders(buyer);
        assertThat(myOrders.get("active")).hasSize(1);
        assertThat(myOrders.get("active").get(0).getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        OrderDto detail = orderService.getOrderDetails(100L, buyer);
        assertThat(detail.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(detail.getItems().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(20));

        product.setPrice(BigDecimal.valueOf(40));
        when(kitchenRepository.findBySeller(seller)).thenReturn(List.of(kitchen));
        when(orderRepository.findByKitchenOrderByCreatedAtDesc(kitchen)).thenReturn(List.of(savedDraft));
        List<OrderDto> sellerOrders = orderService.getSellerOrders(seller);
        assertThat(sellerOrders).hasSize(1);
        assertThat(sellerOrders.get(0).getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
    }

    @Test
    void newOrderUsesCurrentProductPriceAfterPriceChange() {
        User seller = approvedSeller();
        Kitchen kitchen = approvedKitchen(seller);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(30), null);
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
            o.setId(101L);
            return o;
        });

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(1L);
        req.setQuantity(100);

        OrderDto draft = orderService.createOrUpdateDraftOrder(1L, List.of(req), httpSession);
        assertThat(draft.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
    }

    @Test
    void reorderUsesCurrentProductPriceNotOriginal() {
        User seller = approvedSeller();
        Kitchen kitchen = approvedKitchen(seller);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), null);
        product.setId(1L);
        product.setAvailableToday(true);
        product.setRemainingQuantity(100);
        product.setMaxQuantity(100);

        User buyer = new User("Buyer", "9876500001", "A-101", UserRole.BUYER);
        buyer.setId(20L);

        Order original = new Order(buyer, kitchen);
        original.setId(200L);
        original.setOrderStatus(OrderStatus.COMPLETED);
        original.setPaymentStatus(PaymentStatus.PAID);
        OrderItem originalItem = new OrderItem(product, 100, BigDecimal.valueOf(20));
        original.addItem(originalItem);
        original.recalculateTotal();
        when(orderRepository.findById(200L)).thenReturn(Optional.of(original));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(20L)).thenReturn(Optional.of(buyer));

        Order newDraft = new Order(buyer, kitchen);
        newDraft.setId(201L);
        newDraft.setOrderStatus(OrderStatus.DRAFT);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(201L);
            return o;
        });
        when(orderRepository.findById(201L)).thenReturn(Optional.of(newDraft));

        OrderDto reorder = orderService.reorder(200L, httpSession, buyer);
        assertThat(reorder.getItems()).hasSize(1);
        assertThat(reorder.getItems().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(reorder.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(4000));
    }
}
