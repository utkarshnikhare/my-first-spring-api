package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.UpdateDeliveryStatusRequest;
import com.example.my_first_spring_api.dto.UpdatePaymentStatusRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:deep-adversarial-test;DB_CLOSE_DELAY=-1;DEFAULT_LOCK_TIMEOUT=15000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.datasource.hikari.maximum-pool-size=30",
        "sociomart.bootstrap.enabled=false"
})
@ActiveProfiles("test")
public class DeepAdversarialSecurityAndBoundaryTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private SocietyRepository societyRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private Area areaNorth;
    private Area areaSouth;
    private Society societyNorth;
    private Society societySouth;

    private User approvedSellerA;
    private Kitchen kitchenA;
    private MockHttpSession sessionSellerA;

    private User approvedSellerB;
    private Kitchen kitchenB;
    private MockHttpSession sessionSellerB;

    private User pendingSeller;
    private Kitchen pendingKitchen;
    private MockHttpSession sessionPendingSeller;

    private User buyerA;
    private MockHttpSession sessionBuyerA;

    private User buyerB;
    private MockHttpSession sessionBuyerB;

    private MockHttpSession createSession(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, user.getId());
        String role = user.getRole().name();
        if (user.getRole() == UserRole.SELLER && !user.isApprovedSeller()) {
            role = "SELLER_PENDING";
        }
        SecurityContext sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);
        return session;
    }

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

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

        sessionSellerA = createSession(approvedSellerA);

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

        sessionSellerB = createSession(approvedSellerB);

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

        sessionPendingSeller = createSession(pendingSeller);

        // Buyer A (Living in North Society)
        buyerA = new User("Buyer Alice", "980000" + suffix, "Flat 101", UserRole.BUYER);
        buyerA.setAreaRef(areaNorth);
        buyerA.setSocietyRef(societyNorth);
        buyerA.setSociety(societyNorth.getName());
        buyerA.setBuilding("Tower Alpha");
        buyerA.setFlatHouseNumber("101");
        buyerA = userRepository.saveAndFlush(buyerA);

        sessionBuyerA = createSession(buyerA);

        // Buyer B (Living in South Society)
        buyerB = new User("Buyer Bob", "990000" + suffix, "Flat 202", UserRole.BUYER);
        buyerB.setAreaRef(areaSouth);
        buyerB.setSocietyRef(societySouth);
        buyerB.setSociety(societySouth.getName());
        buyerB.setBuilding("Tower Beta");
        buyerB.setFlatHouseNumber("202");
        buyerB = userRepository.saveAndFlush(buyerB);

        sessionBuyerB = createSession(buyerB);
    }

    // =========================================================================
    // SECTION 1: MALICIOUS INPUTS & INJECTION RESISTANCE
    // =========================================================================

    @Test
    @DisplayName("ADV-01: Product creation rejects negative or zero prices via HTTP boundary")
    void productCreationRejectsNegativeOrZeroPrice() throws Exception {
        Map<String, Object> negativePriceDto = Map.of(
                "name", "Negative Price Meal",
                "description", "Should fail",
                "price", -50,
                "availableToday", true
        );

        mvc.perform(post("/api/seller/products?kitchenId=" + kitchenA.getId())
                        .session(sessionSellerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(negativePriceDto)))
                .andExpect(status().isBadRequest());

        Map<String, Object> zeroPriceDto = Map.of(
                "name", "Zero Price Meal",
                "description", "Should fail",
                "price", 0,
                "availableToday", true
        );

        mvc.perform(post("/api/seller/products?kitchenId=" + kitchenA.getId())
                        .session(sessionSellerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(zeroPriceDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ADV-02: Massive Unicode, Emoji and SQL tokens in custom instructions persist safely via HTTP")
    void orderHandlesMassiveUnicodeEmojiAndSqlTokensSafely() throws Exception {
        Product meal = createProduct(kitchenA, "Dal Fry", BigDecimal.valueOf(150), 10);

        // Draft order via HTTP
        List<Map<String, Object>> draftItems = List.of(Map.of("productId", meal.getId(), "quantity", 2));
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(draftItems)))
                .andExpect(status().isOk());

        // Payload with SQL injection tokens, XSS tags, Devanagari text, and Emojis
        String complexInstructions = "स्वादिष्ट भोजन! Please make spicy \uD83C\uDF36\uFE0F\uD83C\uDF5B. "
                + "'; DROP TABLE orders; -- <script>alert('xss')</script> "
                + "Repeat 100 times: " + "A".repeat(200);

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchenA.getId(),
                "items", draftItems,
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi",
                "customInstructions", complexInstructions
        );

        MvcResult result = mvc.perform(post("/api/buyer/orders/place")
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto placed = mapper.readValue(result.getResponse().getContentAsString(), OrderDto.class);
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
    @DisplayName("ADV-03: IDOR Attack - Buyer B cannot inspect or cancel Buyer A's order via HTTP (masked with 404)")
    void buyerBCannotAccessOrCancelBuyerAOrder() throws Exception {
        Product meal = createProduct(kitchenA, "Paneer Butter Masala", BigDecimal.valueOf(220), 10);

        List<Map<String, Object>> draftItems = List.of(Map.of("productId", meal.getId(), "quantity", 1));
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(draftItems)))
                .andExpect(status().isOk());

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchenA.getId(),
                "items", draftItems,
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi",
                "customInstructions", "No onions"
        );

        MvcResult result = mvc.perform(post("/api/buyer/orders/place")
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto orderA = mapper.readValue(result.getResponse().getContentAsString(), OrderDto.class);

        // Buyer B attempts to get order details -> HTTP 404
        mvc.perform(get("/api/buyer/orders/" + orderA.getId())
                        .session(sessionBuyerB))
                .andExpect(status().isNotFound());

        // Buyer B attempts to cancel Buyer A's order -> HTTP 404
        mvc.perform(post("/api/buyer/orders/" + orderA.getId() + "/cancel")
                        .session(sessionBuyerB)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // Order remains intact for Buyer A
        Order checkOrder = orderRepository.findById(orderA.getId()).orElseThrow();
        assertThat(checkOrder.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    @Test
    @DisplayName("ADV-04: IDOR Attack - Seller B cannot mark paid or delivered an order belonging to Kitchen A via HTTP")
    void sellerBCannotMutateKitchenAOrders() throws Exception {
        Product meal = createProduct(kitchenA, "Thali Deluxe", BigDecimal.valueOf(180), 10);

        List<Map<String, Object>> draftItems = List.of(Map.of("productId", meal.getId(), "quantity", 1));
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(draftItems)))
                .andExpect(status().isOk());

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchenA.getId(),
                "items", draftItems,
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi"
        );

        MvcResult result = mvc.perform(post("/api/buyer/orders/place")
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto order = mapper.readValue(result.getResponse().getContentAsString(), OrderDto.class);

        // Seller B attempts to mark order as paid -> HTTP 403 Forbidden
        mvc.perform(patch("/api/seller/orders/" + order.getId() + "/payment-status")
                        .session(sessionSellerB)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // Seller B attempts to update delivery status -> HTTP 403 Forbidden
        Map<String, Object> delivPayload = Map.of("deliveryStatus", "DELIVERED");
        mvc.perform(patch("/api/seller-app/orders/" + order.getId() + "/delivery-status")
                        .session(sessionSellerB)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(delivPayload)))
                .andExpect(status().isForbidden());

        // Verify order payment status remained unchanged
        Order check = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(check.getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);
        assertThat(check.getDeliveryStatus()).isNotEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    @DisplayName("ADV-05: IDOR Attack - Buyer cannot self-verify payment to PAID status via HTTP")
    void buyerCannotSelfVerifyPaymentToPaid() throws Exception {
        Product meal = createProduct(kitchenA, "Biryani", BigDecimal.valueOf(200), 10);

        List<Map<String, Object>> draftItems = List.of(Map.of("productId", meal.getId(), "quantity", 1));
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(draftItems)))
                .andExpect(status().isOk());

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchenA.getId(),
                "items", draftItems,
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi"
        );

        MvcResult result = mvc.perform(post("/api/buyer/orders/place")
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto order = mapper.readValue(result.getResponse().getContentAsString(), OrderDto.class);

        // Buyer attempts to update payment status to PAID -> HTTP 400 Bad Request
        Map<String, Object> payPayload = Map.of("paymentStatus", "PAID");
        mvc.perform(patch("/api/buyer/orders/" + order.getId() + "/payment-status")
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(payPayload)))
                .andExpect(status().isBadRequest());

        Order check = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(check.getPaymentStatus()).isEqualTo(PaymentStatus.AWAITING_SELLER_VERIFICATION);
    }

    // =========================================================================
    // SECTION 3: SERVICE AREA & LIFECYCLE BOUNDARIES
    // =========================================================================

    @Test
    @DisplayName("ADV-06: Cross-society restriction - Buyer B cannot order from Kitchen A (unserved society) via HTTP")
    void buyerBlockedFromOrderingFromUnservedKitchen() throws Exception {
        Product meal = createProduct(kitchenA, "Special Meal", BigDecimal.valueOf(150), 10);

        List<Map<String, Object>> draftItems = List.of(Map.of("productId", meal.getId(), "quantity", 1));

        // Draft creation must fail because Kitchen A does not serve Buyer B's society -> HTTP 400 Bad Request
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                        .session(sessionBuyerB)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(draftItems)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ADV-07: Unapproved / Pending seller cannot access dashboard or appear in public discovery via HTTP")
    void unapprovedSellerBlockedFromOperationsAndDiscovery() throws Exception {
        // Pending seller dashboard access rejected -> HTTP 403 Forbidden
        mvc.perform(get("/api/seller-app/dashboard")
                        .session(sessionPendingSeller))
                .andExpect(status().isForbidden());

        // Ensure pending kitchen does not appear in public discovery
        mvc.perform(get("/api/discovery/kitchens?society=" + societyNorth.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + pendingKitchen.getId() + ")]").doesNotExist());
    }

    // =========================================================================
    // SECTION 4: INVENTORY CONCURRENCY UNDER HEAVY STRESS
    // =========================================================================

    @Test
    @DisplayName("ADV-08: Extreme Stock Contention - 20 concurrent HTTP requests competing for 3 units")
    void extremeStockContentionNeverOversells() throws Exception {
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
            MockHttpSession session = createSession(buyer);

            List<Map<String, Object>> draftItems = List.of(Map.of("productId", scarceItem.getId(), "quantity", 1));
            mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                            .session(session)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(draftItems)))
                    .andExpect(status().isOk());

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

                    Map<String, Object> placePayload = Map.of(
                            "kitchenId", kitchenA.getId(),
                            "items", List.of(Map.of("productId", scarceItem.getId(), "quantity", 1)),
                            "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                            "paymentMethod", "upi"
                    );

                    MvcResult res = mvc.perform(post("/api/buyer/orders/place")
                                    .session(session)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsBytes(placePayload)))
                            .andReturn();

                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
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
    @DisplayName("ADV-09: Seller mark paid is idempotent and rejected on cancelled order via HTTP")
    void sellerMarkPaidIdempotencyAndCancelledGuard() throws Exception {
        Product meal = createProduct(kitchenA, "Kofta Curry", BigDecimal.valueOf(170), 5);

        List<Map<String, Object>> draftItems = List.of(Map.of("productId", meal.getId(), "quantity", 1));
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(draftItems)))
                .andExpect(status().isOk());

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchenA.getId(),
                "items", draftItems,
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi"
        );

        MvcResult result = mvc.perform(post("/api/buyer/orders/place")
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto placed = mapper.readValue(result.getResponse().getContentAsString(), OrderDto.class);

        // First transition to PAID via HTTP -> 200 OK
        mvc.perform(patch("/api/seller/orders/" + placed.getId() + "/payment-status")
                        .session(sessionSellerA)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        // Second transition is idempotent via HTTP -> 200 OK
        mvc.perform(patch("/api/seller/orders/" + placed.getId() + "/payment-status")
                        .session(sessionSellerA)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        // Cancellation on unverified order restores stock but cannot transition back to paid if cancelled
        Product cancelMeal = createProduct(kitchenA, "Sweet Lassi", BigDecimal.valueOf(50), 5);
        List<Map<String, Object>> cancelItems = List.of(Map.of("productId", cancelMeal.getId(), "quantity", 1));
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(cancelItems)))
                .andExpect(status().isOk());

        Map<String, Object> cancelPlacePayload = Map.of(
                "kitchenId", kitchenA.getId(),
                "items", cancelItems,
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi"
        );

        MvcResult cancelRes = mvc.perform(post("/api/buyer/orders/place")
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(cancelPlacePayload)))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto toCancel = mapper.readValue(cancelRes.getResponse().getContentAsString(), OrderDto.class);

        // Buyer cancels order via HTTP
        mvc.perform(post("/api/buyer/orders/" + toCancel.getId() + "/cancel")
                        .session(sessionBuyerA)
                        .with(csrf()))
                .andExpect(status().isOk());

        // Seller attempts to mark cancelled order as PAID -> HTTP 400 Bad Request
        mvc.perform(patch("/api/seller/orders/" + toCancel.getId() + "/payment-status")
                        .session(sessionSellerA)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ADV-10: Rating validation bounds via HTTP - rejected if unpaid, invalid star range, or duplicate")
    void ratingValidationBoundsEnforced() throws Exception {
        Product meal = createProduct(kitchenA, "Paneer Tikka", BigDecimal.valueOf(200), 5);

        List<Map<String, Object>> draftItems = List.of(Map.of("productId", meal.getId(), "quantity", 1));
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(draftItems)))
                .andExpect(status().isOk());

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchenA.getId(),
                "items", draftItems,
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi"
        );

        MvcResult result = mvc.perform(post("/api/buyer/orders/place")
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto placed = mapper.readValue(result.getResponse().getContentAsString(), OrderDto.class);

        // Attempt rating before payment is verified -> HTTP 400 Bad Request
        mvc.perform(post("/api/buyer/orders/" + placed.getId() + "/rate?rating=5")
                        .session(sessionBuyerA)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        // Seller marks paid via HTTP
        mvc.perform(patch("/api/seller/orders/" + placed.getId() + "/payment-status")
                        .session(sessionSellerA)
                        .with(csrf()))
                .andExpect(status().isOk());

        // Rating with 0 -> HTTP 400 Bad Request
        mvc.perform(post("/api/buyer/orders/" + placed.getId() + "/rate?rating=0")
                        .session(sessionBuyerA)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        // Rating with 6 -> HTTP 400 Bad Request
        mvc.perform(post("/api/buyer/orders/" + placed.getId() + "/rate?rating=6")
                        .session(sessionBuyerA)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        // Valid rating with 5 -> HTTP 200 OK
        mvc.perform(post("/api/buyer/orders/" + placed.getId() + "/rate?rating=5")
                        .session(sessionBuyerA)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("COMPLETED"));

        Order ratedOrder = orderRepository.findById(placed.getId()).orElseThrow();
        assertThat(ratedOrder.getRating()).isEqualTo(5);

        // Duplicate rating -> HTTP 400 Bad Request
        mvc.perform(post("/api/buyer/orders/" + placed.getId() + "/rate?rating=4")
                        .session(sessionBuyerA)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ADV-11: Delivery completion is idempotent and updates deliveredAt timestamp via HTTP")
    void deliveryCompletionIdempotencyAndTimestamp() throws Exception {
        Product meal = createProduct(kitchenA, "Thali Simple", BigDecimal.valueOf(120), 5);

        List<Map<String, Object>> draftItems = List.of(Map.of("productId", meal.getId(), "quantity", 1));
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchenA.getId())
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(draftItems)))
                .andExpect(status().isOk());

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchenA.getId(),
                "items", draftItems,
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi"
        );

        MvcResult result = mvc.perform(post("/api/buyer/orders/place")
                        .session(sessionBuyerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto placed = mapper.readValue(result.getResponse().getContentAsString(), OrderDto.class);

        // Mark delivered via HTTP
        Map<String, Object> delivPayload = Map.of("deliveryStatus", "DELIVERED");
        mvc.perform(patch("/api/seller-app/orders/" + placed.getId() + "/delivery-status")
                        .session(sessionSellerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(delivPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"));

        Order dbOrder1 = orderRepository.findById(placed.getId()).orElseThrow();
        LocalDateTime deliveredAt1 = dbOrder1.getDeliveredAt();
        assertThat(deliveredAt1).isNotNull();
        assertThat(dbOrder1.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);

        // Mark delivered again (idempotent) via HTTP
        mvc.perform(patch("/api/seller-app/orders/" + placed.getId() + "/delivery-status")
                        .session(sessionSellerA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(delivPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"));

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

