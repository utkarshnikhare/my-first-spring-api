package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.exception.InvalidKitchenSelectionException;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Requirements 19 & 20 — end-to-end behaviour against a real database:
 * seller notifications on state transitions only (exactly once), and
 * backend-authoritative service-area eligibility for discovery and ordering.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:requirements-19-20;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class Requirements1920IntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;
    @Autowired private NotificationEventRepository notificationEvents;
    @Autowired private OrderService orderService;
    @Autowired private KitchenService kitchenService;
    @Autowired private MarketplaceService marketplaceService;
    @Autowired private SellerService sellerService;
    @Autowired private AdminService adminService;
    @Autowired private SellerAppService sellerApp;

    private static final String T_NEW_ORDER = "New SocioMart order";
    private static final String T_CANCELLED = "Order cancelled";
    private static final String T_PAID = "Payment recorded";
    private static final String T_SOLD_OUT = "Offering sold out";

    private User seller;
    private User buyerIn;
    private User buyerOut;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setSociety("Home Society");
        seller = users.saveAndFlush(seller);
        buyerIn = buyer("BuyerIn" + suffix, "93" + suffix + "01", "Alpha Society");
        buyerOut = buyer("BuyerOut" + suffix, "93" + suffix + "02", "Beta Society");
        kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchen.setSociety("Home Society");
        kitchen.setServiceAreas("Alpha Society");
        kitchen = kitchens.saveAndFlush(kitchen);
    }

    private User buyer(String name, String mobile, String society) {
        User u = new User(name, mobile, "A-1", UserRole.BUYER);
        u.setSociety(society);
        u.setBuilding("A Wing");
        u.setFlatHouseNumber("A-1");
        return users.saveAndFlush(u);
    }

    private Product product(String name, int remaining) {
        Product p = new Product(kitchen, name, "desc", BigDecimal.valueOf(50), null);
        p.setAvailableToday(true);
        p.setRemainingQuantity(remaining);
        p.setMaxQuantity(10);
        return products.saveAndFlush(p);
    }

    private MockHttpSession sessionFor(User buyer) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        return session;
    }

    private Order placeOrderFor(User buyer, Product product, int qty) {
        MockHttpSession session = sessionFor(buyer);
        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(product.getId());
        req.setQuantity(qty);
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(req), session);
        OrderDto dto = orderService.placeOrder(PaymentStatus.PENDING, null, null, session);
        return orders.findById(dto.getId()).orElseThrow();
    }

    private long notifications(Long userId, String title) {
        return notificationEvents.findAll().stream()
                .filter(e -> userId != null && userId.equals(e.getUserId()))
                .filter(e -> title.equals(e.getTitle()))
                .count();
    }

    private KitchenUpdateDto serviceAreas(String areas) {
        KitchenUpdateDto dto = new KitchenUpdateDto();
        dto.setServiceAreas(areas);
        return dto;
    }

    // ==================== Requirement 19: seller notifications ====================

    @Test
    void newOrderNotifiesSellerExactlyOnceAndRetryStaysSilent() {
        Product p = product("N1 Dish", 5);

        placeOrderFor(buyerIn, p, 1);

        assertThat(notifications(seller.getId(), T_NEW_ORDER)).isEqualTo(1);

        // Replaying place on the same session must not duplicate the event.
        MockHttpSession replay = sessionFor(buyerIn);
        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PENDING, null, null, replay))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(notifications(seller.getId(), T_NEW_ORDER)).isEqualTo(1);
    }

    @Test
    void failedOrderProducesNoNotification() {
        Product p = product("N2 Dish", 5);
        MockHttpSession session = sessionFor(buyerIn);
        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(p.getId());
        req.setQuantity(1);
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(req), session);
        // Inventory disappears between draft and placement → placement fails.
        p.setRemainingQuantity(0);
        products.saveAndFlush(p);

        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PENDING, null, null, session))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(notifications(seller.getId(), T_NEW_ORDER)).isZero();
        assertThat(notifications(seller.getId(), T_SOLD_OUT)).isZero();
    }

    @Test
    void cancellationNotifiesSellerExactlyOnce() {
        Order order = placeOrderFor(buyerIn, product("N3 Dish", 5), 1);

        orderService.cancelOrder(order.getId(), buyerIn);
        orderService.cancelOrder(order.getId(), buyerIn); // repeated cancellation

        assertThat(notifications(seller.getId(), T_CANCELLED)).isEqualTo(1);
    }

    @Test
    void paymentTransitionNotifiesOnceAndUnauthorizedStaysSilent() {
        Order order = placeOrderFor(buyerIn, product("N4 Dish", 5), 1);

        User stranger = new User("Stranger", "9100000099", "S-9", UserRole.SELLER);
        stranger.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        users.saveAndFlush(stranger);
        assertThatThrownBy(() -> orderService.markOrderAsPaid(order.getId(), stranger))
                .isInstanceOf(SellerNotAuthorizedException.class);
        assertThat(notifications(buyerIn.getId(), T_PAID)).isZero();

        orderService.markOrderAsPaid(order.getId(), seller);
        orderService.markOrderAsPaid(order.getId(), seller); // repeated Mark as Paid

        assertThat(notifications(buyerIn.getId(), T_PAID)).isEqualTo(1);
    }

    @Test
    void soldOutNotifiesOnceAndReadPathsStaySilent() {
        Product p = product("N5 Dish", 1);

        placeOrderFor(buyerIn, p, 1); // drives remaining quantity to zero
        assertThat(notifications(seller.getId(), T_SOLD_OUT)).isEqualTo(1);

        long before = notifications(seller.getId(), T_SOLD_OUT);
        // Product GET, dashboard refresh and marketplace refresh create nothing.
        kitchenService.getProductById(p.getId(), buyerIn);
        sellerApp.getDashboard(seller);
        marketplaceService.getMarketplaceHome(buyerIn);
        orderService.getSellerOrders(seller);
        assertThat(notifications(seller.getId(), T_SOLD_OUT)).isEqualTo(before);
        assertThat(notifications(seller.getId(), T_NEW_ORDER)).isEqualTo(1);
    }

    // ==================== Requirement 20: service area ====================

    @Test
    void sellerSavesOneAndMultipleSocietiesWithPersistence() {
        sellerService.updateKitchen(kitchen.getId(), serviceAreas("Alpha Society"), seller);
        assertThat(kitchens.findById(kitchen.getId()).orElseThrow().getServiceAreas())
                .isEqualTo("Alpha Society");

        sellerService.updateKitchen(kitchen.getId(), serviceAreas("Alpha Society,Beta Society"), seller);
        assertThat(kitchens.findById(kitchen.getId()).orElseThrow().getServiceAreas())
                .isEqualTo("Alpha Society,Beta Society");
    }

    @Test
    void invalidSocietyRejectedAndAreaUnchanged() {
        assertThatThrownBy(() -> sellerService.updateKitchen(kitchen.getId(), serviceAreas("Nowhere Society"), seller))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(kitchens.findById(kitchen.getId()).orElseThrow().getServiceAreas())
                .isEqualTo("Alpha Society");
    }

    @Test
    void duplicateSelectionsAreNormalizedSafely() {
        sellerService.updateKitchen(kitchen.getId(),
                serviceAreas("beta society,Alpha Society,alpha society,BETA SOCIETY"), seller);

        assertThat(kitchens.findById(kitchen.getId()).orElseThrow().getServiceAreas())
                .isEqualTo("Alpha Society,Beta Society");
    }

    @Test
    void sellerCanRemoveSocieties() {
        sellerService.updateKitchen(kitchen.getId(), serviceAreas("  "), seller);

        assertThat(kitchens.findById(kitchen.getId()).orElseThrow().getServiceAreas()).isEmpty();
    }

    @Test
    void sellerCannotModifyAnotherSellersServiceArea() {
        User intruder = new User("Intruder", "9100000098", "S-8", UserRole.SELLER);
        intruder.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        users.saveAndFlush(intruder);

        assertThatThrownBy(() -> sellerService.updateKitchen(kitchen.getId(), serviceAreas("Beta Society"), intruder))
                .isInstanceOf(SellerNotAuthorizedException.class);

        assertThat(kitchens.findById(kitchen.getId()).orElseThrow().getServiceAreas())
                .isEqualTo("Alpha Society");
    }

    @Test
    void buyerInsideAreaCanDiscoverAndOrder() {
        Product p = product("S6 Dish", 5);

        assertThat(kitchenService.getKitchenDetailById(kitchen.getId(), buyerIn)).isNotNull();
        assertThat(marketplaceService.getAllActiveKitchens(buyerIn))
                .extracting(com.example.my_first_spring_api.dto.KitchenDto::getId).contains(kitchen.getId());

        Order order = placeOrderFor(buyerIn, p, 1);
        assertThat(order.getOrderStatus()).isNotEqualTo(OrderStatus.DRAFT);
    }

    @Test
    void buyerOutsideAreaIsBlockedIncludingDirectUrlsAndApis() {
        Product p = product("S7 Dish", 5);

        // Discovery
        assertThat(marketplaceService.getAllActiveKitchens(buyerOut))
                .extracting(com.example.my_first_spring_api.dto.KitchenDto::getId).doesNotContain(kitchen.getId());
        // Direct kitchen URL / API
        assertThatThrownBy(() -> kitchenService.getKitchenDetailById(kitchen.getId(), buyerOut))
                .isInstanceOf(KitchenNotFoundException.class);
        assertThatThrownBy(() -> kitchenService.getKitchenByName(kitchen.getName(), buyerOut))
                .isInstanceOf(KitchenNotFoundException.class);
        // Direct product API
        assertThatThrownBy(() -> kitchenService.getProductById(p.getId(), buyerOut))
                .isInstanceOf(ProductNotFoundException.class);
        // Direct order API
        MockHttpSession session = sessionFor(buyerOut);
        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(p.getId());
        req.setQuantity(1);
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(req), session))
                .isInstanceOf(InvalidKitchenSelectionException.class);
    }

    @Test
    void changingServiceAreaLeavesExistingOrdersUntouched() {
        Product p = product("S8 Dish", 5);
        Order order = placeOrderFor(buyerIn, p, 1);
        OrderStatus statusBefore = order.getOrderStatus();
        PaymentStatus paymentBefore = order.getPaymentStatus();

        sellerService.updateKitchen(kitchen.getId(), serviceAreas("Beta Society"), seller);

        Order reloaded = orders.findByIdWithItems(order.getId()).orElseThrow();
        assertThat(reloaded.getOrderStatus()).isEqualTo(statusBefore);
        assertThat(reloaded.getPaymentStatus()).isEqualTo(paymentBefore);
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(kitchens.findById(kitchen.getId()).orElseThrow().getServiceAreas())
                .isEqualTo("Beta Society");
    }

    @Test
    void serviceAreaStaysSeparateFromPauseSoldOutAndOrdersClosed() {
        sellerService.pauseKitchen(kitchen.getId(), seller);
        Kitchen paused = kitchens.findById(kitchen.getId()).orElseThrow();
        assertThat(paused.getAvailableToday()).isFalse();
        assertThat(paused.getServiceAreas()).isEqualTo("Alpha Society");
        sellerService.resumeKitchen(kitchen.getId(), seller);

        Product pausedOffering = product("Paused Dish", 5);
        pausedOffering.setOrdersPaused(true);
        pausedOffering = products.saveAndFlush(pausedOffering);
        Product soldOutOffering = product("Soldout Dish", 0);
        String close = LocalTime.now().minusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm"));
        Product closedOffering = product("Closed Dish", 5);
        closedOffering.setOrderWindowEnd(close);
        closedOffering.setCutoffTime(close);
        closedOffering = products.saveAndFlush(closedOffering);

        sellerService.updateKitchen(kitchen.getId(), serviceAreas("Beta Society"), seller);

        Kitchen after = kitchens.findById(kitchen.getId()).orElseThrow();
        assertThat(after.getAvailableToday()).isTrue(); // kitchen pause state untouched
        assertThat(after.getServiceAreas()).isEqualTo("Beta Society");
        assertThat(products.findById(pausedOffering.getId()).orElseThrow().getOrdersPaused()).isTrue();
        assertThat(products.findById(soldOutOffering.getId()).orElseThrow().isSoldOut()).isTrue();
        assertThat(OfferingTiming.lifecycleState(
                products.findById(closedOffering.getId()).orElseThrow(), LocalDate.now(), LocalTime.now()))
                .isEqualTo("ORDERS_CLOSED");
    }

    @Test
    void adminServiceAreaUpdateIsValidatedAndSharesTheSellersSocietyList() {
        assertThatThrownBy(() -> adminService.updateKitchenServiceAreas(kitchen.getId(), "Nowhere Society"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(kitchens.findById(kitchen.getId()).orElseThrow().getServiceAreas())
                .isEqualTo("Alpha Society");

        var updated = adminService.updateKitchenServiceAreas(kitchen.getId(), "Beta Society, beta society");
        assertThat(updated.get("serviceAreas")).isEqualTo("Beta Society");

        assertThat(adminService.societies()).isEqualTo(sellerService.getKnownSocieties());
        assertThat(adminService.societies()).contains("Alpha Society", "Beta Society", "Home Society");
    }
}