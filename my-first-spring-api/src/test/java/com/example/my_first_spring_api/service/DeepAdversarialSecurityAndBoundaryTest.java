package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.controller.SellerAppController;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.UpdatePaymentStatusRequest;
import com.example.my_first_spring_api.exception.*;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:deep-adversarial-test;DB_CLOSE_DELAY=-1;DEFAULT_LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "sociomart.bootstrap.enabled=false"
})
@ActiveProfiles("test")
public class DeepAdversarialSecurityAndBoundaryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private SocietyRepository societyRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    @Autowired private OrderService orderService;
    @Autowired private SellerService sellerService;
    @Autowired private SellerAppService sellerAppService;
    @Autowired private SellerAppController sellerAppController;
    @Autowired private DiscoveryService discoveryService;

    private Area areaNorth;
    private Area areaSouth;
    private Society societyNorth;
    private Society societySouth;

    private User approvedSellerA;
    private Kitchen kitchenA;

    private User approvedSellerB;
    private Kitchen kitchenB;

    private User pendingSeller;
    private Kitchen pendingKitchen;

    private User buyerA;
    private User buyerB;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        kitchenRepository.deleteAll();
        userRepository.deleteAll();
        societyRepository.deleteAll();
        areaRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        areaNorth = areaRepository.saveAndFlush(new Area("North Area " + suffix));
        areaSouth = areaRepository.saveAndFlush(new Area("South Area " + suffix));

        societyNorth = societyRepository.saveAndFlush(new Society(areaNorth, "North Society " + suffix));
        societySouth = societyRepository.saveAndFlush(new Society(areaSouth, "South Society " + suffix));

        // Approved Seller A (North)
        approvedSellerA = new User("Seller Alpha", "910000" + suffix, "A-1", UserRole.SELLER);
        approvedSellerA.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        approvedSellerA.setAreaRef(areaNorth);
        approvedSellerA.setSocietyRef(societyNorth);
        approvedSellerA = userRepository.saveAndFlush(approvedSellerA);

        kitchenA = new Kitchen("kitchen-a-" + suffix, "Kitchen Alpha " + suffix, "North Indian", null, approvedSellerA);
        kitchenA.setSociety(societyNorth.getName());
        kitchenA.setAcceptedPaymentMode(SellerPaymentMode.GPAY_AND_UPI);
        kitchenA.setSellerType(SellerType.KITCHEN);
        kitchenA.setAvailableToday(true);
        kitchenA.getServedSocieties().add(societyNorth);
        kitchenA = kitchenRepository.saveAndFlush(kitchenA);

        // Approved Seller B (South)
        approvedSellerB = new User("Seller Beta", "920000" + suffix, "B-1", UserRole.SELLER);
        approvedSellerB.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        approvedSellerB.setAreaRef(areaSouth);
        approvedSellerB.setSocietyRef(societySouth);
        approvedSellerB = userRepository.saveAndFlush(approvedSellerB);

        kitchenB = new Kitchen("kitchen-b-" + suffix, "Kitchen Beta " + suffix, "South Indian", null, approvedSellerB);
        kitchenB.setSociety(societySouth.getName());
        kitchenB.setAcceptedPaymentMode(SellerPaymentMode.UPI);
        kitchenB.setSellerType(SellerType.KITCHEN);
        kitchenB.setAvailableToday(true);
        kitchenB.getServedSocieties().add(societySouth);
        kitchenB = kitchenRepository.saveAndFlush(kitchenB);

        // Pending Seller (Unapproved)
        pendingSeller = new User("Seller Pending", "930000" + suffix, "P-1", UserRole.SELLER);
        pendingSeller.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        pendingSeller.setAreaRef(areaNorth);
        pendingSeller.setSocietyRef(societyNorth);
        pendingSeller = userRepository.saveAndFlush(pendingSeller);

        pendingKitchen = new Kitchen("kitchen-pending-" + suffix, "Kitchen Pending " + suffix, "Snacks", null, pendingSeller);
        pendingKitchen.setSociety(societyNorth.getName());
        pendingKitchen.setAcceptedPaymentMode(SellerPaymentMode.CASH_ONLY);
        pendingKitchen.setSellerType(SellerType.KITCHEN);
        pendingKitchen = kitchenRepository.saveAndFlush(pendingKitchen);

        // Buyer A (Living in North Society)
        buyerA = new User("Buyer Alice", "980000" + suffix, "Flat 101", UserRole.BUYER);
        buyerA.setAreaRef(areaNorth);
        buyerA.setSocietyRef(societyNorth);
        buyerA.setSociety(societyNorth.getName());
        buyerA.setBuilding("Tower Alpha");
        buyerA.setFlatHouseNumber("101");
        buyerA = userRepository.saveAndFlush(buyerA);

        // Buyer B (Living in South Society)
        buyerB = new User("Buyer Bob", "990000" + suffix, "Flat 202", UserRole.BUYER);
        buyerB.setAreaRef(areaSouth);
        buyerB.setSocietyRef(societySouth);
        buyerB.setSociety(societySouth.getName());
        buyerB.setBuilding("Tower Beta");
        buyerB.setFlatHouseNumber("202");
        buyerB = userRepository.saveAndFlush(buyerB);
    }

    // =========================================================================
    // SECTION 1: MALICIOUS INPUTS & INJECTION RESISTANCE
    // =========================================================================

    @Test
    @DisplayName("ADV-01: Product creation rejects negative or zero prices")
    void productCreationRejectsNegativeOrZeroPrice() {
        ProductCreateDto negativePriceDto = new ProductCreateDto();
        negativePriceDto.setName("Negative Price Meal");
        negativePriceDto.setDescription("Should fail");
        negativePriceDto.setPrice(BigDecimal.valueOf(-50));
        negativePriceDto.setAvailableToday(true);

        assertThatThrownBy(() -> sellerService.createProduct(kitchenA.getId(), negativePriceDto, approvedSellerA))
                .isInstanceOf(IllegalArgumentException.class);

        ProductCreateDto zeroPriceDto = new ProductCreateDto();
        zeroPriceDto.setName("Zero Price Meal");
        zeroPriceDto.setDescription("Should fail");
        zeroPriceDto.setPrice(BigDecimal.ZERO);
        zeroPriceDto.setAvailableToday(true);

        assertThatThrownBy(() -> sellerService.createProduct(kitchenA.getId(), zeroPriceDto, approvedSellerA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ADV-02: Massive Unicode, Emoji and SQL tokens in custom instructions persist safely")
    void orderHandlesMassiveUnicodeEmojiAndSqlTokensSafely() {
        Product meal = createProduct(kitchenA, "Dal Fry", BigDecimal.valueOf(150), 10);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUYER_USER", buyerA.getId());

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(meal.getId());
        item.setQuantity(2);

        orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(item), session);

        // Payload with SQL injection tokens, XSS tags, Devanagari text, and Emojis
        String complexInstructions = "स्वादिष्ट भोजन! Please make spicy \uD83C\uDF36\uFE0F\uD83C\uDF5B. "
                + "'; DROP TABLE orders; -- <script>alert('xss')</script> "
                + "Repeat 100 times: " + "A".repeat(200);

        OrderDto placed = orderService.placeOrder(PaymentStatus.PAID, "upi", null, complexInstructions, session);

        assertThat(placed).isNotNull();
        assertThat(placed.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(placed.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));

        Order reloaded = orderRepository.findById(placed.getId()).orElseThrow();
        assertThat(reloaded.getCustomInstructions()).contains("स्वादिष्ट भोजन!");
        assertThat(reloaded.getCustomInstructions()).contains("'; DROP TABLE orders; --");
        assertThat(reloaded.getCustomInstructions()).contains("<script>alert('xss')</script>");

        // Verify the orders table was not dropped or harmed
        assertThat(orderRepository.count()).isGreaterThanOrEqualTo(1);
    }

    // =========================================================================
    // SECTION 2: CROSS-TENANT IDOR DEFENSE
    // =========================================================================

    @Test
    @DisplayName("ADV-03: IDOR Attack - Buyer B cannot inspect or cancel Buyer A's order")
    void buyerBCannotAccessOrCancelBuyerAOrder() {
        Product meal = createProduct(kitchenA, "Paneer Butter Masala", BigDecimal.valueOf(220), 10);

        MockHttpSession sessionA = new MockHttpSession();
        sessionA.setAttribute("BUYER_USER", buyerA.getId());

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(meal.getId());
        item.setQuantity(1);

        orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(item), sessionA);
        OrderDto orderA = orderService.placeOrder(PaymentStatus.PAID, "upi", null, "No onions", sessionA);

        // Buyer B attempts to get order details
        assertThatThrownBy(() -> orderService.getOrderDetails(orderA.getId(), buyerB))
                .isInstanceOf(OrderNotFoundException.class);

        // Buyer B attempts to cancel Buyer A's order
        assertThatThrownBy(() -> orderService.cancelOrder(orderA.getId(), buyerB))
                .isInstanceOf(OrderNotFoundException.class);

        // Order remains intact for Buyer A
        Order checkOrder = orderRepository.findById(orderA.getId()).orElseThrow();
        assertThat(checkOrder.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    @Test
    @DisplayName("ADV-04: IDOR Attack - Seller B cannot mark paid or delivered an order belonging to Kitchen A")
    void sellerBCannotMutateKitchenAOrders() {
        Product meal = createProduct(kitchenA, "Thali Deluxe", BigDecimal.valueOf(180), 10);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUYER_USER", buyerA.getId());

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(meal.getId());
        item.setQuantity(1);

        orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(item), session);
        OrderDto order = orderService.placeOrder(PaymentStatus.AWAITING_SELLER_VERIFICATION, "upi", null, null, session);

        // Seller B attempts to mark order as paid
        assertThatThrownBy(() -> orderService.markOrderAsPaid(order.getId(), approvedSellerB))
                .isInstanceOf(SellerNotAuthorizedException.class);

        // Seller B attempts to update delivery status
        assertThatThrownBy(() -> sellerAppService.updateDeliveryStatus(approvedSellerB, order.getId(), DeliveryStatus.DELIVERED))
                .isInstanceOf(SellerNotAuthorizedException.class);

        // Verify order payment status remained unchanged
        Order check = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(check.getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);
        assertThat(check.getDeliveryStatus()).isNotEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    @DisplayName("ADV-05: IDOR Attack - Buyer cannot self-verify payment to PAID status")
    void buyerCannotSelfVerifyPaymentToPaid() {
        Product meal = createProduct(kitchenA, "Biryani", BigDecimal.valueOf(200), 10);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUYER_USER", buyerA.getId());

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(meal.getId());
        item.setQuantity(1);

        orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(item), session);
        OrderDto order = orderService.placeOrder(PaymentStatus.AWAITING_SELLER_VERIFICATION, "upi", null, null, session);

        // Buyer attempts to update payment status to PAID
        assertThatThrownBy(() -> orderService.updatePaymentStatus(order.getId(), PaymentStatus.PAID, buyerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment status is managed by the seller");

        Order check = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(check.getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);
    }

    // =========================================================================
    // SECTION 3: SERVICE AREA & LIFECYCLE BOUNDARIES
    // =========================================================================

    @Test
    @DisplayName("ADV-06: Cross-society restriction - Buyer B cannot order from Kitchen A (unserved society)")
    void buyerBlockedFromOrderingFromUnservedKitchen() {
        Product meal = createProduct(kitchenA, "Special Meal", BigDecimal.valueOf(150), 10);

        MockHttpSession sessionB = new MockHttpSession();
        sessionB.setAttribute("BUYER_USER", buyerB.getId());

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(meal.getId());
        item.setQuantity(1);

        // Draft creation must fail because Kitchen A does not serve Buyer B's society
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(item), sessionB))
                .isInstanceOf(InvalidKitchenSelectionException.class)
                .hasMessageContaining("not currently accepting orders in your area");
    }

    @Test
    @DisplayName("ADV-07: Unapproved / Pending seller cannot access dashboard or appear in public discovery")
    void unapprovedSellerBlockedFromOperationsAndDiscovery() {
        MockHttpSession pendingSession = new MockHttpSession();
        pendingSession.setAttribute("BUYER_USER", pendingSeller.getId());

        assertThatThrownBy(() -> sellerAppController.getDashboard(pendingSession))
                .isInstanceOf(SellerNotAuthorizedException.class)
                .hasMessageContaining("awaiting Admin approval");

        // Ensure pending kitchen does not show in discovery
        var discoveredKitchens = discoveryService.getKitchens(societyNorth.getName(), null);
        assertThat(discoveredKitchens.stream().noneMatch(k -> k.getId().equals(pendingKitchen.getId()))).isTrue();
    }

    // =========================================================================
    // SECTION 4: INVENTORY CONCURRENCY UNDER HEAVY STRESS
    // =========================================================================

    @Test
    @DisplayName("ADV-08: Extreme Stock Contention - 20 concurrent threads competing for 3 units")
    void extremeStockContentionNeverOversells() throws InterruptedException {
        Product scarceItem = createProduct(kitchenA, "Rare Special Dish", BigDecimal.valueOf(100), 3);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<MockHttpSession> sessions = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            User buyer = createBuyer("ConcurrentBuyer_" + i);
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("BUYER_USER", buyer.getId());

            OrderItemRequest req = new OrderItemRequest();
            req.setProductId(scarceItem.getId());
            req.setQuantity(1);

            orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(req), session);
            sessions.add(session);
        }

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            final MockHttpSession session = sessions.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    if (index > 0) Thread.sleep(index * 15L); // slight stagger for DB lock acquisition
                    orderService.placeOrder(PaymentStatus.PAID, "upi", null, null, session);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 3 orders must succeed
        assertThat(successCount.get()).isEqualTo(3);
        assertThat(failCount.get()).isEqualTo(17);

        // Product inventory must be strictly 0, never negative
        Product updatedProduct = productRepository.findById(scarceItem.getId()).orElseThrow();
        assertThat(updatedProduct.getRemainingQuantity()).isEqualTo(0);

        // Exactly 3 non-draft orders recorded
        long nonDraftOrders = orderRepository.findByKitchenOrderByCreatedAtDesc(kitchenA).stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT)
                .count();
        assertThat(nonDraftOrders).isEqualTo(3);
    }

    // =========================================================================
    // SECTION 5: PAYMENT LIFECYCLE & RATING BOUNDARY HARDENING
    // =========================================================================

    @Test
    @DisplayName("ADV-09: Seller mark paid is idempotent and rejected on cancelled order")
    void sellerMarkPaidIdempotencyAndCancelledGuard() {
        Product meal = createProduct(kitchenA, "Kofta Curry", BigDecimal.valueOf(170), 5);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUYER_USER", buyerA.getId());

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(meal.getId());
        req.setQuantity(1);

        orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(req), session);
        OrderDto placed = orderService.placeOrder(PaymentStatus.AWAITING_SELLER_VERIFICATION, "upi", null, null, session);

        // First transition to PAID
        OrderDto paid = orderService.markOrderAsPaid(placed.getId(), approvedSellerA);
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Second transition is idempotent
        OrderDto secondPaid = orderService.markOrderAsPaid(placed.getId(), approvedSellerA);
        assertThat(secondPaid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Cancellation on paid order restores stock but cannot transition back to paid if cancelled
        Product cancelMeal = createProduct(kitchenA, "Sweet Lassi", BigDecimal.valueOf(50), 5);
        MockHttpSession cancelSession = new MockHttpSession();
        cancelSession.setAttribute("BUYER_USER", buyerA.getId());
        OrderItemRequest cancelItem = new OrderItemRequest();
        cancelItem.setProductId(cancelMeal.getId());
        cancelItem.setQuantity(1);

        orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(cancelItem), cancelSession);
        OrderDto toCancel = orderService.placeOrder(PaymentStatus.AWAITING_SELLER_VERIFICATION, "upi", null, null, cancelSession);
        orderService.cancelOrder(toCancel.getId(), buyerA);

        assertThatThrownBy(() -> orderService.markOrderAsPaid(toCancel.getId(), approvedSellerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already cancelled");
    }

    @Test
    @DisplayName("ADV-10: Rating validation bounds - rejected if unpaid, invalid star range, or duplicate")
    void ratingValidationBoundsEnforced() {
        Product meal = createProduct(kitchenA, "Paneer Tikka", BigDecimal.valueOf(200), 5);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUYER_USER", buyerA.getId());

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(meal.getId());
        req.setQuantity(1);

        orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(req), session);
        OrderDto placed = orderService.placeOrder(PaymentStatus.AWAITING_SELLER_VERIFICATION, "upi", null, null, session);

        // Attempt rating before payment is verified -> Rejected
        assertThatThrownBy(() -> orderService.rateOrder(placed.getId(), 5, buyerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only after completing the payment");

        // Seller marks paid
        orderService.markOrderAsPaid(placed.getId(), approvedSellerA);

        // Rating with 0 -> Rejected
        assertThatThrownBy(() -> orderService.rateOrder(placed.getId(), 0, buyerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rating must be between 1 and 5");

        // Rating with 6 -> Rejected
        assertThatThrownBy(() -> orderService.rateOrder(placed.getId(), 6, buyerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rating must be between 1 and 5");

        // Valid rating with 5 -> Success
        OrderDto rated = orderService.rateOrder(placed.getId(), 5, buyerA);
        assertThat(rated.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);

        Order ratedOrder = orderRepository.findById(placed.getId()).orElseThrow();
        assertThat(ratedOrder.getRating()).isEqualTo(5);

        // Duplicate rating -> Rejected
        assertThatThrownBy(() -> orderService.rateOrder(placed.getId(), 4, buyerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already rated this order");
    }

    @Test
    @DisplayName("ADV-11: Delivery completion is idempotent and updates deliveredAt timestamp")
    void deliveryCompletionIdempotencyAndTimestamp() {
        Product meal = createProduct(kitchenA, "Thali Simple", BigDecimal.valueOf(120), 5);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("BUYER_USER", buyerA.getId());

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(meal.getId());
        req.setQuantity(1);

        orderService.createOrUpdateDraftOrder(kitchenA.getId(), List.of(req), session);
        OrderDto placed = orderService.placeOrder(PaymentStatus.PAID, "upi", null, null, session);

        // Mark delivered
        OrderDto delivered1 = sellerAppService.updateDeliveryStatus(approvedSellerA, placed.getId(), DeliveryStatus.DELIVERED);
        assertThat(delivered1.getDeliveryStatus()).isEqualTo("DELIVERED");

        Order dbOrder1 = orderRepository.findById(placed.getId()).orElseThrow();
        LocalDateTime deliveredAt1 = dbOrder1.getDeliveredAt();
        assertThat(deliveredAt1).isNotNull();
        assertThat(dbOrder1.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);

        // Mark delivered again (idempotent)
        OrderDto delivered2 = sellerAppService.updateDeliveryStatus(approvedSellerA, placed.getId(), DeliveryStatus.DELIVERED);
        assertThat(delivered2.getDeliveryStatus()).isEqualTo("DELIVERED");

        Order dbOrder2 = orderRepository.findById(placed.getId()).orElseThrow();
        assertThat(dbOrder2.getDeliveredAt()).isEqualTo(deliveredAt1); // Timestamp preserved exactly
        assertThat(dbOrder2.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Product createProduct(Kitchen kitchen, String name, BigDecimal price, int stock) {
        Product product = new Product(kitchen, name, "Delicious", price, null);
        product.setAvailableToday(true);
        product.setAvailableDate(LocalDate.now());
        product.setRemainingQuantity(stock);
        product.setMaxQuantity(stock);
        product.setCutoffTime("23:59");
        product.setOrderWindowEnd("23:59");
        product.setOrdersPaused(false);
        return productRepository.saveAndFlush(product);
    }

    private User createBuyer(String name) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User buyer = new User(name, "97" + suffix, "Flat " + suffix, UserRole.BUYER);
        buyer.setAreaRef(areaNorth);
        buyer.setSocietyRef(societyNorth);
        buyer.setSociety(societyNorth.getName());
        buyer.setBuilding("Tower North");
        buyer.setFlatHouseNumber("Flat " + suffix);
        return userRepository.saveAndFlush(buyer);
    }
}
