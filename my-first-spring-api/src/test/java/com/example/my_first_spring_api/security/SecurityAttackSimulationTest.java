package com.example.my_first_spring_api.security;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import com.example.my_first_spring_api.service.LocationService;
import com.example.my_first_spring_api.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Phase 4 Adversarial Security and Business-Logic Attack Simulation Test.
 * Covers:
 * - Unauthenticated and post-logout access attempts on protected endpoints
 * - IDOR and cross-tenant attacks (Buyer A vs Buyer B, Seller X vs Seller Y)
 * - State and payment status tampering (buyer forcing PAID status)
 * - Input validation and injection attacks (negative prices, zero quantities, XSS scripts)
 * - Cross-society boundary enforcement
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security-attack-sim;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
public class SecurityAttackSimulationTest {

    private static final String PASSWORD = "StrongPass123!";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;
    @Autowired private LocationService locations;
    @Autowired private OrderService orderService;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    private Long areaAId;
    private Long societyAId;
    private Long areaBId;
    private Long societyBId;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        var areaA = locations.createArea("Area A " + suffix);
        var socA = locations.createSociety(areaA.getId(), "Society A " + suffix);
        areaAId = areaA.getId();
        societyAId = socA.getId();

        var areaB = locations.createArea("Area B " + suffix);
        var socB = locations.createSociety(areaB.getId(), "Society B " + suffix);
        areaBId = areaB.getId();
        societyBId = socB.getId();
    }

    @Test
    @DisplayName("Attack 1: Unauthenticated callers are rejected on protected buyer, seller, and admin endpoints")
    void testUnauthenticatedAccessRejected() throws Exception {
        // Buyer draft access without session
        mvc.perform(get("/api/buyer/orders/draft"))
                .andExpect(status().isUnauthorized());

        // Buyer place order without session
        mvc.perform(post("/api/buyer/orders/place").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // Seller dashboard access without session
        mvc.perform(get("/api/seller/app/dashboard"))
                .andExpect(status().isUnauthorized());

        // Admin sellers management without admin session
        mvc.perform(get("/api/admin/sellers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Attack 2: Post-logout invalidated session cannot access protected endpoints")
    void testPostLogoutSessionCannotAccessEndpoints() throws Exception {
        String mobile = uniqueMobile();
        MockHttpSession session = registerBuyer(mobile, societyAId, areaAId);

        // Buyer profile check while logged in
        mvc.perform(get("/api/buyer/profile").session(session))
                .andExpect(status().isOk());

        // Log out
        mvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Old session should now be rejected
        mvc.perform(get("/api/buyer/orders/draft").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Attack 3: IDOR - Buyer B cannot cancel Buyer A's order (masked with 404)")
    void testIdorCrossBuyerOrderCancellationRejected() throws Exception {
        // Create Seller and product in Society A
        User seller = createApprovedSeller("SellerA-" + suffix, uniqueMobile(), societyAId, areaAId);
        Kitchen kitchen = createKitchen(seller, "Kitchen A " + suffix, societyAId);
        Product product = createProduct(kitchen, "Meal A", BigDecimal.valueOf(150), 10);

        // Buyer A registers and places an order
        String buyerAMobile = uniqueMobile();
        MockHttpSession sessionA = registerBuyer(buyerAMobile, societyAId, areaAId);
        User buyerA = users.findByMobileNumber(buyerAMobile).orElseThrow();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(2);
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(item), sessionA);

        PlaceOrderRequest.BuyerDetails bd = new PlaceOrderRequest.BuyerDetails();
        bd.setName(buyerA.getName());
        bd.setMobileNumber(buyerA.getMobileNumber());
        bd.setSociety("Society A " + suffix);
        bd.setBuilding("Tower A");
        bd.setFlatHouseNumber("A-101");
        var placedOrder = orderService.placeOrder(PaymentStatus.PENDING, null, bd, "Special test", sessionA);

        // Buyer B registers
        String buyerBMobile = uniqueMobile();
        MockHttpSession sessionB = registerBuyer(buyerBMobile, societyAId, areaAId);

        // Buyer B maliciously attempts to cancel Buyer A's order
        mvc.perform(post("/api/buyer/orders/" + placedOrder.getId() + "/cancel").with(csrf()).session(sessionB))
                .andExpect(status().isNotFound());

        // Verify order is still ORDERED in DB
        Order dbOrder = orders.findById(placedOrder.getId()).orElseThrow();
        assertThat(dbOrder.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    @Test
    @DisplayName("Attack 4: IDOR - Buyer B cannot view Buyer A's order details")
    void testIdorCrossBuyerOrderDetailsAccessRejected() throws Exception {
        User seller = createApprovedSeller("SellerB-" + suffix, uniqueMobile(), societyAId, areaAId);
        Kitchen kitchen = createKitchen(seller, "Kitchen B " + suffix, societyAId);
        Product product = createProduct(kitchen, "Meal B", BigDecimal.valueOf(120), 10);

        String buyerAMobile = uniqueMobile();
        MockHttpSession sessionA = registerBuyer(buyerAMobile, societyAId, areaAId);
        User buyerA = users.findByMobileNumber(buyerAMobile).orElseThrow();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(1);
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(item), sessionA);

        PlaceOrderRequest.BuyerDetails bd = new PlaceOrderRequest.BuyerDetails();
        bd.setName(buyerA.getName());
        bd.setMobileNumber(buyerA.getMobileNumber());
        bd.setSociety("Society A " + suffix);
        bd.setBuilding("Tower A");
        bd.setFlatHouseNumber("A-202");
        var placedOrder = orderService.placeOrder(PaymentStatus.PENDING, null, bd, "Test", sessionA);

        String buyerBMobile = uniqueMobile();
        MockHttpSession sessionB = registerBuyer(buyerBMobile, societyAId, areaAId);

        // Buyer B attempts to read Buyer A's order details
        mvc.perform(get("/api/buyer/orders/" + placedOrder.getId()).session(sessionB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Attack 5: Business Logic - Buyer cannot bypass seller confirmation by forcing paymentStatus=PAID")
    void testBuyerCannotForcePaymentStatusPaid() throws Exception {
        User seller = createApprovedSeller("SellerC-" + suffix, uniqueMobile(), societyAId, areaAId);
        Kitchen kitchen = createKitchen(seller, "Kitchen C " + suffix, societyAId);
        Product product = createProduct(kitchen, "Meal C", BigDecimal.valueOf(250), 10);

        String buyerMobile = uniqueMobile();
        MockHttpSession session = registerBuyer(buyerMobile, societyAId, areaAId);
        User buyer = users.findByMobileNumber(buyerMobile).orElseThrow();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(1);
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(item), session);

        PlaceOrderRequest.BuyerDetails bd = new PlaceOrderRequest.BuyerDetails();
        bd.setName(buyer.getName());
        bd.setMobileNumber(buyer.getMobileNumber());
        bd.setSociety("Society A " + suffix);
        bd.setBuilding("Tower C");
        bd.setFlatHouseNumber("C-303");
        var placedOrder = orderService.placeOrder(PaymentStatus.PENDING, null, bd, "Test", session);

        // Buyer attempts to update payment status to PAID directly via PATCH
        mvc.perform(patch("/api/buyer/orders/" + placedOrder.getId() + "/payment-status")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\": \"PAID\"}"))
                .andExpect(status().isBadRequest());

        // Verify order in DB still has PENDING status
        Order dbOrder = orders.findById(placedOrder.getId()).orElseThrow();
        assertThat(dbOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Attack 6: IDOR - Seller Y cannot mark Seller X's order as PAID")
    void testSellerCannotMarkOtherSellersOrderAsPaid() throws Exception {
        User sellerX = createApprovedSeller("SellerX-" + suffix, uniqueMobile(), societyAId, areaAId);
        Kitchen kitchenX = createKitchen(sellerX, "Kitchen X " + suffix, societyAId);
        Product productX = createProduct(kitchenX, "Item X", BigDecimal.valueOf(100), 10);

        User sellerY = createApprovedSeller("SellerY-" + suffix, uniqueMobile(), societyAId, areaAId);
        MockHttpSession sessionSellerY = loginUser(sellerY.getMobileNumber(), PASSWORD);

        String buyerMobile = uniqueMobile();
        MockHttpSession sessionBuyer = registerBuyer(buyerMobile, societyAId, areaAId);
        User buyer = users.findByMobileNumber(buyerMobile).orElseThrow();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productX.getId());
        item.setQuantity(1);
        orderService.createOrUpdateDraftOrder(kitchenX.getId(), List.of(item), sessionBuyer);

        PlaceOrderRequest.BuyerDetails bd = new PlaceOrderRequest.BuyerDetails();
        bd.setName(buyer.getName());
        bd.setMobileNumber(buyer.getMobileNumber());
        bd.setSociety("Society A " + suffix);
        bd.setBuilding("Tower X");
        bd.setFlatHouseNumber("X-101");
        var placedOrder = orderService.placeOrder(PaymentStatus.PENDING, null, bd, "Test", sessionBuyer);

        // Seller Y maliciously attempts to mark Seller X's order as paid
        mvc.perform(patch("/api/seller/orders/" + placedOrder.getId() + "/payment-status")
                        .with(csrf())
                        .session(sessionSellerY))
                .andExpect(status().isForbidden());

        // DB order remains PENDING
        Order dbOrder = orders.findById(placedOrder.getId()).orElseThrow();
        assertThat(dbOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Attack 7: Input Validation - Negative product prices are rejected with 400 Bad Request")
    void testNegativePriceRejected() throws Exception {
        User seller = createApprovedSeller("SellerD-" + suffix, uniqueMobile(), societyAId, areaAId);
        Kitchen kitchen = createKitchen(seller, "Kitchen D " + suffix, societyAId);
        MockHttpSession sellerSession = loginUser(seller.getMobileNumber(), PASSWORD);

        Map<String, Object> badProduct = Map.of(
                "name", "Illegal Price Item",
                "price", -50.00,
                "offeringDate", LocalDate.now().toString(),
                "deliverySlot", "Lunch",
                "kitchenId", kitchen.getId()
        );

        mvc.perform(post("/api/seller/products?kitchenId=" + kitchen.getId())
                        .with(csrf())
                        .session(sellerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badProduct)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Attack 8: Input Validation - Zero and negative order quantities are rejected with 400 Bad Request")
    void testZeroOrNegativeQuantityRejected() throws Exception {
        User seller = createApprovedSeller("SellerE-" + suffix, uniqueMobile(), societyAId, areaAId);
        Kitchen kitchen = createKitchen(seller, "Kitchen E " + suffix, societyAId);
        Product product = createProduct(kitchen, "Meal E", BigDecimal.valueOf(180), 10);

        String buyerMobile = uniqueMobile();
        MockHttpSession buyerSession = registerBuyer(buyerMobile, societyAId, areaAId);

        // Quantity 0
        String zeroQtyJson = "[{\"productId\": " + product.getId() + ", \"quantity\": 0}]";
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchen.getId())
                        .with(csrf())
                        .session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zeroQtyJson))
                .andExpect(status().isBadRequest());

        // Negative quantity
        String negQtyJson = "[{\"productId\": " + product.getId() + ", \"quantity\": -3}]";
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchen.getId())
                        .with(csrf())
                        .session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(negQtyJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Attack 9: Cross-Society Isolation - Buyer from Society B cannot order from Kitchen serving only Society A")
    void testCrossSocietyOrderPlacementRejected() throws Exception {
        User seller = createApprovedSeller("SellerF-" + suffix, uniqueMobile(), societyAId, areaAId);
        Kitchen kitchen = createKitchen(seller, "Kitchen F " + suffix, societyAId); // serves only Society A
        Product product = createProduct(kitchen, "Meal F", BigDecimal.valueOf(220), 10);

        // Buyer from Society B
        String buyerMobile = uniqueMobile();
        MockHttpSession buyerSession = registerBuyer(buyerMobile, societyBId, areaBId);
        User buyerB = users.findByMobileNumber(buyerMobile).orElseThrow();

        // Attempting to draft items for foreign kitchen is rejected with 400 Bad Request
        String itemsJson = "[{\"productId\": " + product.getId() + ", \"quantity\": 1}]";
        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchen.getId())
                        .with(csrf())
                        .session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Attack 10: XSS Injection - Script tags in order instructions are stored safely without execution")
    void testXssScriptInOrderInstructionsHandledSafely() throws Exception {
        User seller = createApprovedSeller("SellerG-" + suffix, uniqueMobile(), societyAId, areaAId);
        Kitchen kitchen = createKitchen(seller, "Kitchen G " + suffix, societyAId);
        Product product = createProduct(kitchen, "Meal G", BigDecimal.valueOf(190), 10);

        String buyerMobile = uniqueMobile();
        MockHttpSession buyerSession = registerBuyer(buyerMobile, societyAId, areaAId);
        User buyer = users.findByMobileNumber(buyerMobile).orElseThrow();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(1);
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(item), buyerSession);

        String xssPayload = "<script>alert('XSS-TEST')</script><img src='x' onerror='alert(1)'/>";

        PlaceOrderRequest.BuyerDetails bd = new PlaceOrderRequest.BuyerDetails();
        bd.setName(buyer.getName());
        bd.setMobileNumber(buyer.getMobileNumber());
        bd.setSociety("Society A " + suffix);
        bd.setBuilding("Tower A");
        bd.setFlatHouseNumber("A-505");

        MvcResult result = mvc.perform(post("/api/buyer/orders/place")
                        .with(csrf())
                        .session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "buyerDetails", bd,
                                "customInstructions", xssPayload,
                                "paymentStatus", "PENDING"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        // Server accepts safely and treats instructions as text
        OrderDto placed = objectMapper.readValue(result.getResponse().getContentAsString(), OrderDto.class);
        assertThat(placed.getCustomInstructions()).isEqualTo(xssPayload);

        Order dbOrder = orders.findById(placed.getId()).orElseThrow();
        assertThat(dbOrder.getCustomInstructions()).isEqualTo(xssPayload);
    }

    @Test
    @DisplayName("Attack 7: Mass assignment attempt via buyer profile update cannot elevate role or approval")
    void attack_massAssignmentRoleElevationAttempt() throws Exception {
        String mobile = uniqueMobile();
        MockHttpSession buyerSession = registerBuyer(mobile, societyAId, areaAId);

        User buyerBefore = users.findByMobileNumber(mobile).orElseThrow();
        assertThat(buyerBefore.getRole()).isEqualTo(UserRole.BUYER);

        // Malicious payload attempting to inject role ADMIN and APPROVED seller status
        Map<String, Object> maliciousProfile = Map.of(
                "name", "Attacker Buyer",
                "role", "ADMIN",
                "sellerApprovalStatus", "APPROVED",
                "flatHouseNumber", "Fl-999"
        );

        mvc.perform(put("/api/buyer/profile").with(csrf())
                        .session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(maliciousProfile)))
                .andExpect(status().isOk());

        // Verify database entity is not elevated
        User buyerAfter = users.findByMobileNumber(mobile).orElseThrow();
        assertThat(buyerAfter.getRole()).isEqualTo(UserRole.BUYER);
        assertThat(buyerAfter.getSellerApprovalStatus()).isNotEqualTo(SellerApprovalStatus.APPROVED);

        // Attempting to access admin endpoints with this buyer session must still be rejected (403 Forbidden)
        mvc.perform(get("/api/admin/overview")
                        .session(buyerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Attack 8: Client-supplied price tampering in order requests is completely ignored")
    void attack_clientPriceTamperingIgnored() throws Exception {
        String sellerMobile = uniqueMobile();
        User seller = createApprovedSeller("Legit Seller " + suffix, sellerMobile, societyAId, areaAId);
        Kitchen kitchen = createKitchen(seller, "Price Test Kitchen " + suffix, societyAId);
        BigDecimal realPrice = new BigDecimal("150.00");
        Product product = createProduct(kitchen, "Special Item " + suffix, realPrice, 10);

        String buyerMobile = uniqueMobile();
        MockHttpSession buyerSession = registerBuyer(buyerMobile, societyAId, areaAId);

        // Buyer attempts to tamper with item price ($0.01 instead of $150.00)
        Map<String, Object> tamperedItem = Map.of(
                "productId", product.getId(),
                "quantity", 2,
                "price", new BigDecimal("0.01"),
                "totalPrice", new BigDecimal("0.02")
        );

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchen.getId(),
                "items", List.of(tamperedItem),
                "totalPrice", new BigDecimal("0.02"),
                "paymentStatus", "PENDING",
                "buyerDetails", Map.of(
                        "name", "Buyer " + suffix,
                        "mobileNumber", buyerMobile,
                        "flatHouseNumber", "Fl-1",
                        "building", "B-1",
                        "society", "Society A " + suffix
                )
        );

        MvcResult result = mvc.perform(post("/api/buyer/orders/place").with(csrf())
                        .session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isOk())
                .andReturn();

        OrderDto placed = objectMapper.readValue(result.getResponse().getContentAsString(), OrderDto.class);
        // Authoritative total must be 2 * 150.00 = 300.00, ignoring 0.02
        assertThat(placed.getTotalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(placed.getItems()).hasSize(1);
        assertThat(placed.getItems().get(0).getPrice()).isEqualByComparingTo(realPrice);

        Order dbOrder = orders.findByIdWithItems(placed.getId()).orElseThrow();
        assertThat(dbOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(dbOrder.getItems()).hasSize(1);
        assertThat(dbOrder.getItems().get(0).getPrice()).isEqualByComparingTo(realPrice);
    }

    @Test
    @DisplayName("Attack 9: Invalid state transition - cancelled order cannot be moved to CONFIRMED")
    void attack_invalidStateTransitionFromCancelled() throws Exception {
        String sellerMobile = uniqueMobile();
        User seller = createApprovedSeller("Status Seller " + suffix, sellerMobile, societyAId, areaAId);
        Kitchen kitchen = createKitchen(seller, "Status Kitchen " + suffix, societyAId);
        Product product = createProduct(kitchen, "Status Item " + suffix, new BigDecimal("50.00"), 10);

        String buyerMobile = uniqueMobile();
        MockHttpSession buyerSession = registerBuyer(buyerMobile, societyAId, areaAId);

        // Place an order
        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchen.getId(),
                "items", List.of(Map.of("productId", product.getId(), "quantity", 1)),
                "paymentStatus", "PENDING",
                "buyerDetails", Map.of(
                        "name", "Buyer " + suffix,
                        "mobileNumber", buyerMobile,
                        "flatHouseNumber", "Fl-1",
                        "building", "B-1",
                        "society", "Society A " + suffix
                )
        );

        MvcResult placeRes = mvc.perform(post("/api/buyer/orders/place").with(csrf())
                        .session(buyerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isOk())
                .andReturn();
        OrderDto placed = objectMapper.readValue(placeRes.getResponse().getContentAsString(), OrderDto.class);

        // Buyer cancels the order
        mvc.perform(post("/api/buyer/orders/" + placed.getId() + "/cancel").with(csrf())
                        .session(buyerSession))
                .andExpect(status().isOk());

        Order cancelledOrder = orders.findById(placed.getId()).orElseThrow();
        assertThat(cancelledOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Seller logs in
        MockHttpSession sellerSession = loginUser(sellerMobile, PASSWORD);

        // Seller attempts to transition CANCELLED order to CONFIRMED
        mvc.perform(patch("/api/seller/orders/" + placed.getId() + "/status").with(csrf())
                        .session(sellerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("orderStatus", "CONFIRMED"))))
                .andExpect(status().isBadRequest());

        // Verify order in database is still CANCELLED
        Order finalOrder = orders.findById(placed.getId()).orElseThrow();
        assertThat(finalOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // Helper methods
    private MockHttpSession registerBuyer(String mobile, Long socId, Long arId) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register/buyer").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "name", "Buyer " + suffix,
                                "mobileNumber", mobile,
                                "password", PASSWORD,
                                "flatHouseNumber", "Fl-1",
                                "building", "B-1",
                                "areaId", arId,
                                "societyId", socId))))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    private User createApprovedSeller(String name, String mobile, Long socId, Long arId) {
        User seller = new User(name, mobile, "Fl-S", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(PASSWORD));
        seller.setAreaRef(locations.findActiveAreas().stream().filter(a -> a.getId().equals(arId)).findFirst().orElse(null));
        seller.setSocietyRef(locations.findActiveSocieties(arId).stream().filter(s -> s.getId().equals(socId)).findFirst().orElse(null));
        seller.setSociety(seller.getSocietyRef() != null ? seller.getSocietyRef().getName() : "Soc");
        return users.saveAndFlush(seller);
    }

    private MockHttpSession loginUser(String mobile, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "mobileNumber", mobile,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    private Kitchen createKitchen(User seller, String name, Long socId) {
        Kitchen kitchen = new Kitchen("k-" + UUID.randomUUID().toString().substring(0, 8), name, "desc", null, seller);
        var soc = locations.findActiveSocieties(seller.getAreaRef().getId()).stream()
                .filter(s -> s.getId().equals(socId)).findFirst().orElse(null);
        if (soc != null) {
            kitchen.setSociety(soc.getName());
            kitchen.getServedSocieties().add(soc);
        }
        kitchen.setAvailableToday(true);
        return kitchens.saveAndFlush(kitchen);
    }

    private Product createProduct(Kitchen kitchen, String name, BigDecimal price, int qty) {
        Product p = new Product(kitchen, name, "desc", price, null);
        p.setAvailableToday(true);
        p.setAvailableDate(LocalDate.now());
        p.setOrderWindowEnd("23:59");
        p.setReadyByTime(LocalDate.now().atTime(23, 59).toString());
        p.setRemainingQuantity(qty);
        p.setMaxQuantity(qty);
        return products.saveAndFlush(p);
    }

    private static String uniqueMobile() {
        return "9" + String.format("%09d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000_000);
    }
}
