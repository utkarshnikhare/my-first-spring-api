package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
import com.example.my_first_spring_api.exception.OrderNotFoundException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1 DEMO BLOCKER regression - "Place Order" silently did nothing.
 *
 * Root cause
 * ----------
 * The in-progress order is tracked by a session attribute (DRAFT_ORDER_ID) that
 * sits NEXT TO the buyer identity (BUYER_USER) in the SAME HTTP session. The
 * Buyer and Seller apps are same-origin and therefore share one session, so a
 * Seller login (the Seller app's /api/seller-app/demo-login) replaced BUYER_USER
 * with the seller while leaving the buyer's draft pointer in place.
 *
 * The Buyer tab still held a stale state.user, so withAuthGate/requireAuth
 * believed the session was fine and posted straight to /api/buyer/orders/place.
 * OrderService.placeOrder then compared the draft's buyer against the SELLER
 * identity, failed the ownership check, and threw OrderNotFoundException -> 404.
 *
 * 404 was the worst possible answer: the frontend recovers from 401 but has no
 * path for 404, so the buyer stayed on Confirm Order pressing a button that
 * failed identically forever. Reproduced live - retrying the click returned the
 * same 404 and the screen never recovered.
 *
 * These tests pin the corrected behaviour:
 *  - a valid buyer can still place an order, with a single inventory decrement;
 *  - a non-buyer identity can neither start nor place an order (so a seller
 *    account can never silently become the order's buyer);
 *  - an orphaned draft is reported as a RECOVERABLE expired order session, not
 *    as a 404, and the unusable session pointer is cleared;
 *  - the recovery path creates exactly ONE order and decrements stock once.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:buyer-checkout-blocker;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class BuyerCheckoutSessionRecoveryTest {

    private static final String SOCIETY = "Sunshine Society";

    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;

    private User seller;
    private User buyer;
    private User otherBuyer;
    private Kitchen kitchen;
    private Product product;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.saveAndFlush(seller);

        kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchen.setSociety(SOCIETY);
        kitchen = kitchens.saveAndFlush(kitchen);

        buyer = new User("Buyer" + suffix, "93" + suffix + "01", "A-402", UserRole.BUYER);
        buyer.setSociety(SOCIETY);
        buyer.setBuilding("A Wing");
        buyer = users.saveAndFlush(buyer);

        otherBuyer = new User("Other" + suffix, "94" + suffix + "01", "B-1", UserRole.BUYER);
        otherBuyer.setSociety(SOCIETY);
        otherBuyer.setBuilding("B Wing");
        otherBuyer = users.saveAndFlush(otherBuyer);

        product = new Product(kitchen, "Kerala Sadya", "desc", BigDecimal.valueOf(200), "plate");
        product.setAvailableToday(true);
        product.setRemainingQuantity(12);
        product.setMaxQuantity(12);
        product = products.saveAndFlush(product);
    }

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, user.getId());
        return session;
    }

    private MockHttpSession buyerSession() {
        return sessionFor(buyer);
    }

    private OrderDto draft(MockHttpSession session) {
        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);
        return orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(req), session);
    }

    // ------------------------------------------------------------------
    // The normal path must keep working
    // ------------------------------------------------------------------

    @Test
    void aValidBuyerStillPlacesTheOrderWithASingleInventoryDecrement() {
        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto placed = orderService.placeOrder(PaymentStatus.PAID, null, null, session);

        assertThat(placed.getId()).isNotNull();
        assertThat(placed.getOrderNumber()).isNotBlank();
        assertThat(placed.getBuyer().getMobileNumber()).isEqualTo(buyer.getMobileNumber());
        assertThat(placed.getBuyer().getSociety()).isEqualTo(SOCIETY);
        assertThat(placed.getKitchen().getId()).isEqualTo(kitchen.getId());
        assertThat(placed.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(placed.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        // Transaction-time price, not a client-supplied one.
        assertThat(placed.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(placed.getItems().get(0).getPrice()).isEqualByComparingTo("200.00");
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(11);
        assertThat(nonDraftOrderCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // The reported defect
    // ------------------------------------------------------------------

    @Test
    void aSellerIdentityThatReplacedTheSharedSessionCannotPlaceTheBuyersOrder() {
        MockHttpSession session = buyerSession();
        draft(session);

        // The Seller app logs in on the same origin/session.
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());

        // Must be refused - and refused as an AUTH failure, so the frontend has a
        // recovery path, instead of a 404 that dead-ends the checkout.
        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PAID, null, null, session))
                .isInstanceOf(BuyerNotAuthenticatedException.class);

        // Nothing was created and no stock was consumed.
        assertThat(nonDraftOrderCount()).isZero();
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity())
                .as("a refused place order must not touch inventory").isEqualTo(12);
    }

    @Test
    void aSellerIdentityCannotEvenStartADraft() {
        // Otherwise the seller's own account would silently become the order buyer.
        MockHttpSession session = sessionFor(seller);

        assertThatThrownBy(() -> draft(session))
                .isInstanceOf(BuyerNotAuthenticatedException.class);
        assertThat(ordersInThisTest()).isZero();
    }

    /**
     * Orders created by THIS test. The H2 database is shared across the methods
     * in this class (DB_CLOSE_DELAY=-1), so counting every row would include
     * rows left by earlier tests. Each test creates its own uniquely named
     * kitchen, so scoping to it isolates the count without extra rollback.
     */
    private long ordersInThisTest() {
        return orders.findByKitchenOrderByCreatedAtDesc(kitchen).size();
    }

    private long nonDraftOrderCount() {
        return orders.findByKitchenOrderByCreatedAtDesc(kitchen).stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT)
                .count();
    }

    // ------------------------------------------------------------------
    // Recovery: the orphaned draft must be re-drivable, exactly once
    // ------------------------------------------------------------------

    @Test
    void aNonBuyerIdentityInTheSharedSessionIsRefusedAsAnAuthProblemNotAs404() {
        // The Seller app logs in on the same origin, replacing the identity.
        // This must be an ORDINARY 401 that the frontend already knows how to
        // recover from - not the cross-buyer 404, which has no recovery path and
        // left the buyer stuck on a permanently failing Place Order button.
        MockHttpSession session = buyerSession();
        draft(session);
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());

        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PAID, null, null, session))
                .isInstanceOf(BuyerNotAuthenticatedException.class)
                .isNotInstanceOf(OrderNotFoundException.class);

        // The now-unreachable pointer is dropped so a retry starts clean.
        assertThat(session.getAttribute(OrderService.DRAFT_ORDER_SESSION_KEY)).isNull();
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(12);
        assertThat(nonDraftOrderCount()).isZero();
    }

    @Test
    void theBoundedRecoveryCreatesExactlyOneOrderAndDecrementsStockOnce() {
        // Mirrors the frontend's single recovery: sign back in as the buyer,
        // rebuild the draft from the cart, then place.
        MockHttpSession session = buyerSession();
        OrderDto abandoned = draft(session);

        session.setAttribute(BuyerService.BUYER_SESSION_KEY, seller.getId());
        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PAID, null, null, session))
                .isInstanceOf(BuyerNotAuthenticatedException.class);

        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        OrderDto rebuilt = draft(session);
        OrderDto placed = orderService.placeOrder(PaymentStatus.PAID, null, null, session);

        assertThat(placed.getId()).isNotNull();
        assertThat(placed.getBuyer().getMobileNumber()).isEqualTo(buyer.getMobileNumber());
        assertThat(placed.getTotalAmount()).isEqualByComparingTo("200.00");

        // Exactly one live order and one decrement. The rebuilt draft is REUSED
        // as the placed order (existing idempotent design); the orphaned draft
        // stays a dead DRAFT row and never becomes a second order.
        assertThat(nonDraftOrderCount()).isEqualTo(1);
        assertThat(ordersInThisTest()).isEqualTo(2);
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(11);
        assertThat(placed.getId()).isEqualTo(rebuilt.getId());
        assertThat(placed.getId()).isNotEqualTo(abandoned.getId());
    }

    @Test
    void anUnauthenticatedSessionCanNeitherDraftNorPlace() {
        MockHttpSession session = new MockHttpSession();

        assertThatThrownBy(() -> draft(session))
                .isInstanceOf(BuyerNotAuthenticatedException.class);
        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PAID, null, null, session))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ordersInThisTest()).isZero();
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(12);
    }

    @Test
    void aRepeatedPlaceOrderStillCannotCreateASecondOrder() {
        MockHttpSession session = buyerSession();
        draft(session);

        OrderDto placed = orderService.placeOrder(PaymentStatus.PAID, null, null, session);
        assertThat(placed.getId()).isNotNull();

        // Second click on the consumed draft session.
        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PAID, null, null, session))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(nonDraftOrderCount()).isEqualTo(1);
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(11);
    }
}
