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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Requirement 19 — seller notifications fire only on successful state transitions,
 * exactly once, and never on read/refresh paths.
 */
class Requirement19NotificationTest {

    @Mock OrderRepository orderRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock AnalyticsService analyticsService;
    @Mock NotificationService notificationService;
    @Mock HttpSession httpSession;

    @InjectMocks OrderService orderService;

    private final Map<String, Object> sessionMap = new java.util.HashMap<>();

    private User seller;
    private User buyer;
    private Kitchen kitchen;

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

        seller = new User("Seller", "9100000001", "S-1", UserRole.SELLER);
        seller.setId(10L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        buyer = new User("Buyer", "9876500001", "A-101", UserRole.BUYER);
        buyer.setId(20L);
        buyer.setSociety("Sunshine Society");
        buyer.setBuilding("A Wing");
        buyer.setFlatHouseNumber("A-101");
        kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        kitchen.setAvailableToday(true);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(userRepository.findById(20L)).thenReturn(Optional.of(buyer));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product product(int remaining) {
        Product p = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(20), null);
        p.setId(1L);
        p.setAvailableToday(true);
        p.setRemainingQuantity(remaining);
        p.setMaxQuantity(50);
        return p;
    }

    private Order draftWith(Product product, int itemCopies) {
        Order draft = new Order(buyer, kitchen);
        draft.setId(100L);
        draft.setOrderStatus(OrderStatus.DRAFT);
        draft.setOrderNumber("SM-DRAFT-1");
        for (int i = 0; i < itemCopies; i++) {
            draft.getItems().add(new OrderItem(product, 1, BigDecimal.valueOf(20)));
        }
        return draft;
    }

    private void startDraftSession(Order draft) {
        sessionMap.clear();
        sessionMap.put(OrderService.DRAFT_ORDER_SESSION_KEY, draft.getId());
        sessionMap.put("BUYER_USER", buyer.getId());
        when(orderRepository.findByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));
    }

    @Test
    void failedOrderProducesNoNotification() {
        Product p = product(5);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.consumeStock(1L, 1)).thenReturn(0); // insufficient stock at commit time
        Order draft = draftWith(p, 1);
        startDraftSession(draft);

        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PENDING, null, null, httpSession))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(notificationService);
    }

    @Test
    void duplicateEntriesInOneOrderNotifySoldOutOnlyOnce() {
        Product p = product(2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.consumeStock(any(Long.class), any(Integer.class))).thenAnswer(inv -> {
            p.setRemainingQuantity(0); // simulate consuming the last units
            return 1;
        });
        Order draft = draftWith(p, 2); // same product listed twice in one draft
        startDraftSession(draft);

        orderService.placeOrder(PaymentStatus.PENDING, null, null, httpSession);

        verify(notificationService, times(1)).sendSoldOutNotification(eq(seller), eq("Poha"));
        verify(notificationService, times(1)).sendNewOrderNotification(eq(seller), any(), any());
    }

    @Test
    void repeatedBuyerCancellationNotifiesOnlyOnce() {
        Order order = new Order(buyer, kitchen);
        order.setId(50L);
        order.setOrderNumber("SM-CANCEL-1");
        order.setOrderStatus(OrderStatus.ORDERED);
        when(orderRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdWithItems(50L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(50L, buyer);
        orderService.cancelOrder(50L, buyer); // idempotent retry

        verify(notificationService, times(1))
                .sendOrderCancellationNotification(eq(seller), eq("SM-CANCEL-1"));
    }

    @Test
    void repeatedSellerCancellationNotifiesOnlyOnce() {
        Order order = new Order(buyer, kitchen);
        order.setId(51L);
        order.setOrderNumber("SM-CANCEL-2");
        order.setOrderStatus(OrderStatus.ORDERED);
        when(orderRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdWithItems(51L)).thenReturn(Optional.of(order));

        orderService.updateOrderStatus(51L, OrderStatus.CANCELLED, seller);
        assertThatThrownBy(() -> orderService.updateOrderStatus(51L, OrderStatus.CANCELLED, seller))
                .isInstanceOf(IllegalArgumentException.class);

        verify(notificationService, times(1))
                .sendOrderCancellationNotification(eq(seller), eq("SM-CANCEL-2"));
    }

    @Test
    void unauthorizedPaymentUpdateNotifiesNoOne() {
        Order order = new Order(buyer, kitchen);
        order.setId(52L);
        order.setOrderNumber("SM-PAID-1");
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findByIdForUpdate(52L)).thenReturn(Optional.of(order));

        User stranger = new User("Stranger", "9100000099", "S-9", UserRole.SELLER);
        stranger.setId(99L);
        stranger.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);

        assertThatThrownBy(() -> orderService.markOrderAsPaid(52L, stranger))
                .isInstanceOf(com.example.my_first_spring_api.exception.SellerNotAuthorizedException.class);

        verifyNoInteractions(notificationService);
    }

    @Test
    void repeatedMarkAsPaidNotifiesOnlyOnce() {
        Order order = new Order(buyer, kitchen);
        order.setId(53L);
        order.setOrderNumber("SM-PAID-2");
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findByIdForUpdate(53L)).thenReturn(Optional.of(order));

        orderService.markOrderAsPaid(53L, seller);
        orderService.markOrderAsPaid(53L, seller); // repeated Mark as Paid

        verify(notificationService, times(1))
                .sendPaymentReceivedNotification(eq(buyer), eq("SM-PAID-2"));
    }

    @Test
    void paymentTransitionNotifiesOwningSellerExactlyOnce() {
        Order order = new Order(buyer, kitchen);
        order.setId(55L);
        order.setOrderNumber("SM-PAID-3");
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(order));

        orderService.markOrderAsPaid(55L, seller);
        orderService.markOrderAsPaid(55L, seller); // repeated Mark as Paid

        // Requirement 19: seller is the correct recipient, and the existing buyer
        // notification type keeps working unchanged.
        verify(notificationService, times(1))
                .sendPaymentUpdatedNotification(eq(seller), eq("SM-PAID-3"));
        verify(notificationService, times(1))
                .sendPaymentReceivedNotification(eq(buyer), eq("SM-PAID-3"));
    }

    @Test
    void alreadyPaidOrderNotifiesNobody() {
        Order order = new Order(buyer, kitchen);
        order.setId(56L);
        order.setOrderNumber("SM-PAID-4");
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findByIdForUpdate(56L)).thenReturn(Optional.of(order));

        orderService.markOrderAsPaid(56L, seller);

        verifyNoInteractions(notificationService);
    }

    @Test
    void readOnlySellerPathsCreateNoNotifications() {
        Order order = new Order(buyer, kitchen);
        order.setId(54L);
        order.setOrderNumber("SM-READ-1");
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.getItems().add(new OrderItem(product(5), 1, BigDecimal.valueOf(20)));

        // Dashboard/order-list refresh and a plain order GET must stay silent.
        when(kitchenRepository.findBySeller(seller)).thenReturn(List.of(kitchen));
        when(orderRepository.findByKitchenOrderByCreatedAtDesc(kitchen)).thenReturn(List.of(order));
        when(orderRepository.findById(54L)).thenReturn(Optional.of(order));

        orderService.getSellerOrders(seller);
        orderService.getOrderDtoForSeller(54L, seller);

        verifyNoInteractions(notificationService);
    }
}
