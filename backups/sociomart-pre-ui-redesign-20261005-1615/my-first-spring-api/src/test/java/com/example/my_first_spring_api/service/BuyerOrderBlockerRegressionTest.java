package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.BuyerProfileDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.exception.BuyerProfileIncompleteException;
import com.example.my_first_spring_api.exception.InvalidKitchenSelectionException;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
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
 * P0 DEMO BLOCKER regression - the Buyer could not complete an order.
 *
 * Root cause: /api/auth/me (and /api/auth/demo-login) returned an
 * AuthResponseDto without the buyer's persisted society/building, so the client
 * built its identity state with no service area. The Profile screen then
 * pre-filled the society field with a hardcoded placeholder and saving the form
 * overwrote the buyer's real society, after which the (correct) server-side
 * service-area validation rejected the kitchen.
 *
 * These tests pin the authoritative behaviour:
 *  - the profile round-trip keeps the real society/building, so the client can
 *    never round-trip a placeholder over a real service area;
 *  - a matching society is accepted and a genuinely non-matching one is still
 *    rejected (validation is NOT weakened);
 *  - ordering succeeds, decrements inventory exactly once, and a duplicate
 *    submission does not create a second order.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:buyer-order-blocker;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class BuyerOrderBlockerRegressionTest {

    private static final String SOCIETY = "Sunshine Society";

    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired BuyerService buyerService;
    @Autowired OrderService orderService;

    private User seller;
    private User buyer;
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

        product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), null);
        product.setAvailableToday(true);
        product.setRemainingQuantity(10);
        product.setMaxQuantity(10);
        product = products.saveAndFlush(product);
    }

    private MockHttpSession buyerSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        return session;
    }

    private OrderDto draft(MockHttpSession session) {
        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(product.getId());
        req.setQuantity(1);
        return orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(req), session);
    }


    // ------------------------------------------------------------------
    // Profile state is authoritative and survives a save round-trip
    // ------------------------------------------------------------------

    @Test
    void profileRoundTripKeepsTheRealSocietyAndBuilding() {
        var profile = buyerService.getProfile(buyerSession());
        assertThat(profile.getSociety()).isEqualTo(SOCIETY);
        assertThat(profile.getBuilding()).isEqualTo("A Wing");

        // Simulate the Profile screen saving back exactly what it was shown.
        BuyerProfileDto dto = new BuyerProfileDto();
        dto.setName(buyer.getName());
        dto.setSociety(profile.getSociety());
        dto.setBuilding(profile.getBuilding());
        dto.setFlatHouseNumber(profile.getFlatHouseNumber());
        var saved = buyerService.updateProfile(dto, buyerSession());

        // The real service area must survive the round-trip unchanged.
        assertThat(saved.getSociety()).isEqualTo(SOCIETY);
        assertThat(saved.getBuilding()).isEqualTo("A Wing");
        assertThat(users.findById(buyer.getId()).orElseThrow().getSociety()).isEqualTo(SOCIETY);
    }

    // ------------------------------------------------------------------
    // A valid buyer can reach and complete Place Order
    // ------------------------------------------------------------------

    @Test
    void validProfileReachesPlaceOrderAndSucceedsWithSingleInventoryDecrement() {
        MockHttpSession session = buyerSession();
        assertThat(draft(session).getId()).isNotNull();

        var placed = orderService.placeOrder(PaymentStatus.PAID, null, "note", session);

        assertThat(placed.getId()).isNotNull();
        assertThat(placed.getOrderNumber()).isNotBlank();
        assertThat(placed.getKitchen().getId()).isEqualTo(kitchen.getId());
        assertThat(placed.getBuyer().getSociety()).isEqualTo(SOCIETY);
        assertThat(placed.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(placed.getTotalAmount()).isEqualByComparingTo("40.00");

        // Inventory changes exactly once.
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(9);
    }

    // ------------------------------------------------------------------
    // Duplicate submission does not create a second order
    // ------------------------------------------------------------------

    @Test
    void duplicatePlaceOrderSubmissionDoesNotCreateASecondOrder() {
        MockHttpSession session = buyerSession();
        draft(session);

        long ordersBefore = orders.count();
        orderService.placeOrder(PaymentStatus.PAID, null, null, session);
        // The draft row is reused as the placed order - no extra row.
        assertThat(orders.count()).isEqualTo(ordersBefore);

        // Second click on the same (already consumed) draft session.
        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PAID, null, null, session))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(orders.count()).isEqualTo(ordersBefore);
        // No double inventory decrement.
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(9);
    }

    // ------------------------------------------------------------------
    // A genuinely non-matching society is still rejected (not weakened)
    // ------------------------------------------------------------------

    @Test
    void genuinelyNonMatchingSocietyIsStillRejected() {
        buyer.setSociety("Green Valley");
        buyer = users.saveAndFlush(buyer);

        MockHttpSession session = buyerSession();
        // Draft creation itself is service-area checked, so a buyer outside the
        // kitchen's society can never even start a draft.
        assertThatThrownBy(() -> draft(session))
                .isInstanceOf(InvalidKitchenSelectionException.class);

        // Nothing was ordered and no stock was touched.
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(10);
    }

    @Test
    void serviceAreaChangeAfterDraftIsRejectedAtPlacement() {
        MockHttpSession session = buyerSession();
        draft(session);

        // Buyer's society changes to a non-matching one before placement.
        buyer.setSociety("Green Valley");
        buyer = users.saveAndFlush(buyer);

        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PAID, null, null, session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not serve your selected area");

        // Rejected before stock was touched.
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(10);
    }

    // ------------------------------------------------------------------
    // Existing profile-completeness validation remains intact
    // ------------------------------------------------------------------

    @Test
    void incompleteProfileStillBlocksOrdering() {
        buyer.setBuilding(null);
        buyer = users.saveAndFlush(buyer);

        MockHttpSession session = buyerSession();
        draft(session);

        assertThatThrownBy(() -> orderService.placeOrder(PaymentStatus.PAID, null, null, session))
                .isInstanceOf(BuyerProfileIncompleteException.class);

        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(10);
    }
}
