package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.exception.BuyerProfileIncompleteException;
import com.example.my_first_spring_api.exception.OrderNotFoundException;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock RetentionService retentionService;
    @Mock HttpSession httpSession;

    @InjectMocks OrderService orderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        User buyer = new User("Buyer", "9876543210", "A-101", UserRole.BUYER);
        buyer.setId(20L);
        when(httpSession.getAttribute(any(String.class))).thenAnswer(invocation ->
                "BUYER_USER".equals(invocation.getArgument(0)) ? 20L : null);
        when(userRepository.findById(20L)).thenReturn(Optional.of(buyer));
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
    void scheduledOfferingCannotEnterDraftBeforeOrdersOpenFrom() {
        Kitchen kitchen = approvedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Scheduled Poha", "desc", BigDecimal.valueOf(40), null);
        product.setId(1L);
        product.setAvailableDate(LocalDate.now(AppTimeZone.ZONE));
        product.setAvailableToday(true);
        product.setIsPreorder(false);
        product.setOrdersOpenFrom(LocalDateTime.now(AppTimeZone.ZONE).plusHours(1));
        product.setOrderWindowEnd("23:59");
        product.setRemainingQuantity(10);
        product.setMaxQuantity(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(1L);
        request.setQuantity(1);
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(1L, List.of(request), httpSession))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("open on");
        verify(orderRepository, never()).save(any(Order.class));
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
            User staleBuyer = new User("Buyer", "9876543210", "A-101", UserRole.BUYER);
            staleBuyer.setId(20L);
            setBuyer(staleBuyer);
        }}));

        OrderItemRequest req2 = new OrderItemRequest();
        req2.setProductId(2L);
        req2.setQuantity(1);
        OrderDto draft2 = orderService.createOrUpdateDraftOrder(2L, List.of(req2), httpSession);
        assertThat(draft2.getKitchen().getId()).isEqualTo(2L);
        assertThat(draft2.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(50));
        verify(orderRepository).delete(any(Order.class));
    }

    @Test
    void buyerCannotReorderAnotherBuyersOrder() {
        User buyer = new User("Buyer", "9876500001", "A-101", UserRole.BUYER);
        buyer.setId(20L);
        User otherBuyer = new User("Other Buyer", "9876500002", "A-102", UserRole.BUYER);
        otherBuyer.setId(21L);
        User seller = new User("Seller", "9100000010", "S-1", UserRole.SELLER);
        seller.setId(10L);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        Order foreignOrder = new Order(otherBuyer, kitchen);
        foreignOrder.setId(44L);
        when(orderRepository.findById(44L)).thenReturn(Optional.of(foreignOrder));

        assertThatThrownBy(() -> orderService.reorder(44L, httpSession, buyer))
                .isInstanceOf(OrderNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void buyerCannotReadOrClearAnotherBuyersDraft() {
        User currentBuyer = new User("Buyer", "9876500001", "A-101", UserRole.BUYER);
        currentBuyer.setId(20L);
        User otherBuyer = new User("Other Buyer", "9876500002", "A-102", UserRole.BUYER);
        otherBuyer.setId(21L);
        User seller = new User("Seller", "9100000010", "S-1", UserRole.SELLER);
        seller.setId(10L);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        Order foreignDraft = new Order(otherBuyer, kitchen);
        foreignDraft.setId(45L);
        when(httpSession.getAttribute(OrderService.DRAFT_ORDER_SESSION_KEY)).thenReturn(45L);
        when(orderRepository.findById(45L)).thenReturn(Optional.of(foreignDraft));

        assertThatThrownBy(() -> orderService.getCurrentDraftOrder(httpSession))
                .isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> orderService.clearDraftOrder(httpSession))
                .isInstanceOf(OrderNotFoundException.class);
        verify(orderRepository, never()).deleteById(45L);
    }

    @Test
    void buyerCannotPlaceAnotherBuyersDraft() {
        User currentBuyer = new User("Buyer", "9876500001", "A-101", UserRole.BUYER);
        currentBuyer.setId(20L);
        User otherBuyer = new User("Other Buyer", "9876500002", "A-102", UserRole.BUYER);
        otherBuyer.setId(21L);
        User seller = new User("Seller", "9100000010", "S-1", UserRole.SELLER);
        seller.setId(10L);
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        Order foreignDraft = new Order(otherBuyer, kitchen);
        foreignDraft.setId(46L);
        when(httpSession.getAttribute(OrderService.DRAFT_ORDER_SESSION_KEY)).thenReturn(46L);
        when(orderRepository.findByIdForUpdate(46L)).thenReturn(Optional.of(foreignDraft));

        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PENDING, null, null, null, httpSession))
                .isInstanceOf(OrderNotFoundException.class);
        verify(orderRepository, never()).delete(any());
    }

    @Test
    void incompleteProfileBlocksOrderPlacement() {
        User seller = approvedSeller();
        Kitchen kitchen = approvedKitchen();
        kitchen.setId(1L);
        kitchen.setSociety("Sunshine Society");
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), null);
        product.setId(1L);
        product.setAvailableToday(true);
        product.setRemainingQuantity(10);
        product.setMaxQuantity(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Buyer with missing society
        User incompleteBuyer = new User("Buyer", "9876543210", "A-101", UserRole.BUYER);
        incompleteBuyer.setId(20L);
        // society, building, flatHouseNumber all null
        when(userRepository.findById(20L)).thenReturn(Optional.of(incompleteBuyer));

        // Mock save to assign ID to draft
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(100L);
            return o;
        });

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(1L);
        req.setQuantity(1);

        OrderDto draft = orderService.createOrUpdateDraftOrder(1L, List.of(req), httpSession);
        assertThat(draft.getId()).isEqualTo(100L);

        when(httpSession.getAttribute(OrderService.DRAFT_ORDER_SESSION_KEY)).thenReturn(100L);
        // resolveBuyer() reads BUYER_USER from session, then looks up the user
        when(httpSession.getAttribute("BUYER_USER")).thenReturn(incompleteBuyer.getId());
        when(userRepository.findById(incompleteBuyer.getId())).thenReturn(Optional.of(incompleteBuyer));
        Order mockOrder = new Order();
        mockOrder.setId(100L);
        mockOrder.setKitchen(kitchen);
        mockOrder.setBuyer(incompleteBuyer);
        mockOrder.setOrderStatus(OrderStatus.DRAFT);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setPrice(product.getPrice());
        mockOrder.setItems(List.of(item));
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(mockOrder));

        assertThrows(BuyerProfileIncompleteException.class, () -> {
            orderService.placeOrder(PaymentStatus.WILL_PAY_LATER, null, null, null, httpSession);
        });
    }

    @Test
    void endToEndOrderPlacementWithCartItemsPopulatesAllRequiredColumnsCleanly() {
        Kitchen kitchen = approvedKitchen();
        kitchen.setId(1L);
        kitchen.setSociety("Sunshine Society");
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        Product product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), null);
        product.setId(1L);
        product.setAvailableToday(true);
        product.setRemainingQuantity(10);
        product.setMaxQuantity(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.consumeStock(any(Long.class), any(Integer.class))).thenReturn(1);

        User buyer = new User("Rahul Sharma", "9876543210", "A-101", UserRole.BUYER);
        buyer.setId(20L);
        buyer.setSociety("Sunshine Society");
        buyer.setBuilding("Tower 1");
        buyer.setFlatHouseNumber("A-101");
        when(userRepository.findById(20L)).thenReturn(Optional.of(buyer));
        when(httpSession.getAttribute("BUYER_USER")).thenReturn(20L);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(100L);
            o.ensureNonNullFields();
            return o;
        });

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(1L);
        itemReq.setQuantity(2);

        OrderDto draftDto = orderService.createOrUpdateDraftOrder(1L, List.of(itemReq), httpSession);
        assertThat(draftDto.getId()).isEqualTo(100L);

        Order draftOrder = new Order();
        draftOrder.setId(100L);
        draftOrder.setKitchen(kitchen);
        draftOrder.setBuyer(buyer);
        draftOrder.setOrderStatus(OrderStatus.DRAFT);
        draftOrder.setOrderNumber("ORD-100-TEST");
        OrderItem orderItem = new OrderItem();
        orderItem.setId(10L);
        orderItem.setOrder(draftOrder);
        orderItem.setProduct(product);
        orderItem.setQuantity(2);
        orderItem.setPrice(product.getPrice());
        draftOrder.setItems(new java.util.ArrayList<>(List.of(orderItem)));

        when(httpSession.getAttribute(OrderService.DRAFT_ORDER_SESSION_KEY)).thenReturn(100L);
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(draftOrder));

        PlaceOrderRequest.BuyerDetails details = new PlaceOrderRequest.BuyerDetails();
        details.setName("Rahul Sharma");
        details.setPhone("9876543210");
        details.setSociety("Sunshine Society");
        details.setBuilding("Tower 1");
        details.setFlatHouseNumber("A-101");
        details.setDeliveryAddress("A-101, Tower 1, Sunshine Society");

        OrderDto placedDto = orderService.placeOrder(PaymentStatus.PENDING, "COD", details, "Leave at door", httpSession);

        assertThat(placedDto).isNotNull();
        assertThat(placedDto.getId()).isEqualTo(100L);
        assertThat(placedDto.getOrderNumber()).isNotBlank();
        assertThat(placedDto.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(placedDto.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order finalOrder = orderCaptor.getValue();

        assertThat(finalOrder.getBuyer()).isNotNull();
        assertThat(finalOrder.getKitchen()).isNotNull();
        assertThat(finalOrder.getOrderNumber()).isNotBlank();
        assertThat(finalOrder.getOrderStatus()).isNotNull().isNotEqualTo(OrderStatus.DRAFT);
        assertThat(finalOrder.getPaymentStatus()).isNotNull();
        assertThat(finalOrder.getStatus()).isNotNull();
        assertThat(finalOrder.getDeliveryAddress()).isNotBlank();
        assertThat(finalOrder.getSpecialInstructions()).isEqualTo("Leave at door");
        assertThat(finalOrder.getItems()).isNotEmpty();
        for (OrderItem item : finalOrder.getItems()) {
            assertThat(item.getProduct()).isNotNull();
            assertThat(item.getPrice()).isNotNull().isGreaterThan(BigDecimal.ZERO);
            assertThat(item.getQuantity()).isNotNull().isGreaterThan(0);
        }
    }

    @Test
    void orderEntityPrePersistDefaultsEnsureNonNullConstraints() {
        Order order = new Order();
        order.ensureNonNullFields();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getSpecialInstructions()).isEqualTo("");
        assertThat(order.getDeliveryAddress()).isEqualTo("");
        assertThat(order.getOrderNumber()).isNotBlank();
    }
}
