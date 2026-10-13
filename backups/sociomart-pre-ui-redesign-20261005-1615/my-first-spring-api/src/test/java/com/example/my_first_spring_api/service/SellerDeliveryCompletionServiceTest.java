package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.DeliveryProgressDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemDetailDto;
import com.example.my_first_spring_api.exception.OrderNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.DeliveryStatus;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.SellerType;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seller Delivery Completion Tracker (V1) - service level.
 *
 * <p>Pins the semantics the seller screen and the buyer screen both read from
 * the SAME {@link Order} row:</p>
 * <ul>
 *   <li>delivery is an INDEPENDENT axis: marking an order Delivered never marks
 *       it paid, and delivering an unpaid order is legal;</li>
 *   <li>the transition is idempotent and the original {@code deliveredAt}
 *       survives every repeated write, including a repeated "Mark All";</li>
 *   <li>cancelled orders and unplaced drafts take no part in delivery tracking
 *       (never counted as progress, never marked, never in either filter
 *       bucket);</li>
 *   <li>the bulk action is scoped to ONE offering on ONE date - never to the
 *       seller's current UI filters - and reports the rows it actually
 *       changed;</li>
 *   <li>ownership is enforced from the authenticated seller for both the single
 *       and the bulk write;</li>
 *   <li>Kitchen and Homemade sellers share one implementation, because the flag
 *       lives on the common Order row;</li>
 *   <li>the buyer reads the same flag back through their own order endpoints.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seller-delivery-completion;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SellerDeliveryCompletionServiceTest {

    private static final String SOCIETY_A = "Sunshine Society";
    private static final String SOCIETY_B = "Green Valley";

    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired SellerAppService sellerApp;
    @Autowired OrderService orderService;

    private User seller;
    private User otherSeller;
    private User homemadeSeller;
    private User buyerA;
    private User buyerB;
    private Kitchen kitchen;
    private Kitchen otherKitchen;
    private Kitchen homemadeKitchen;
    private Product poha;
    private Product masala;
    private Product otherPoha;
    private Product pickle;

    @BeforeEach
    void setUp() {
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        seller = approvedSeller("Seller" + s, "91" + s + "01");
        otherSeller = approvedSeller("Other" + s, "92" + s + "01");
        homemadeSeller = approvedSeller("Home" + s, "95" + s + "01");

        kitchen = kitchen(seller, "k" + s, SOCIETY_A, SellerType.KITCHEN);
        otherKitchen = kitchen(otherSeller, "ok" + s, SOCIETY_B, SellerType.KITCHEN);
        // A Homemade Product seller is a different seller category with its own
        // single kitchen, but it writes to the very same Order row, so delivery
        // tracking must work identically for it.
        homemadeKitchen = kitchen(homemadeSeller, "hk" + s, SOCIETY_A, SellerType.HOMEMADE_PRODUCTS);

        buyerA = buyer("BuyerA" + s, "93" + s + "01", SOCIETY_A, "A Wing", "A-402");
        buyerB = buyer("BuyerB" + s, "94" + s + "01", SOCIETY_B, "B Wing", "B-602");

        poha = product(kitchen, "Poha");
        masala = product(kitchen, "Masala");
        otherPoha = product(otherKitchen, "Other Poha");
        pickle = product(homemadeKitchen, "Homemade Pickle");
    }

    private User approvedSeller(String name, String mobile) {
        User u = new User(name, mobile, "S-1", UserRole.SELLER);
        u.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return users.saveAndFlush(u);
    }

    private Kitchen kitchen(User owner, String slug, String society, SellerType type) {
        Kitchen k = new Kitchen(slug, "Kitchen " + slug, "", null, owner);
        k.setSociety(society);
        k.setSellerType(type);
        return kitchens.saveAndFlush(k);
    }

    private User buyer(String name, String mobile, String society, String building, String flat) {
        User u = new User(name, mobile, flat, UserRole.BUYER);
        u.setSociety(society);
        u.setBuilding(building);
        u.setFlatHouseNumber(flat);
        return users.saveAndFlush(u);
    }

    private Product product(Kitchen owner, String name) {
        Product p = new Product(owner, name, "desc", BigDecimal.valueOf(40), "plate");
        p.setAvailableToday(true);
        p.setRemainingQuantity(50);
        p.setMaxQuantity(50);
        return products.saveAndFlush(p);
    }

    /** One order for the default Kitchen offering, dated today. */
    private Order place(User who, int qty, PaymentStatus payment, OrderStatus status) {
        return place(who, poha, qty, payment, status);
    }

    private Order place(User who, Product offering, int qty, PaymentStatus payment, OrderStatus status) {
        Order o = new Order(who, offering.getKitchen());
        o.setOrderNumber("DL" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        o.setOrderStatus(status);
        o.setPaymentStatus(payment);
        o.addItem(new OrderItem(offering, qty, BigDecimal.valueOf(40)));
        o.recalculateTotal();
        return orders.saveAndFlush(o);
    }

    /**
     * Moves an already-persisted order to another day. {@code createdAt} is set by
     * the entity's {@code @PrePersist} hook, so the row has to exist before it can
     * be dated - exactly how production reaches another day, through elapsed time.
     */
    private Order onDay(Order order, LocalDate date) {
        order.setCreatedAt(date.atTime(12, 0));
        return orders.saveAndFlush(order);
    }

    private DeliveryStatus storedStatus(Long orderId) {
        return orders.findById(orderId).orElseThrow().getEffectiveDeliveryStatus();
    }

    private DeliveryProgressDto progress(Product offering) {
        return progress(seller, offering);
    }

    private DeliveryProgressDto progress(User owner, Product offering) {
        return orderService.getDeliveryProgress(offering.getId(), LocalDate.now(), owner);
    }

    private OrderItemDetailDto detail(String society, String status, String delivery) {
        return sellerApp.getOrderItemDetail(seller, poha.getId(), LocalDate.now(), society, status, delivery);
    }

    private OrderItemDetailDto.CustomerOrderRow rowFor(OrderItemDetailDto dto, Long orderId) {
        return dto.getCustomers().stream()
                .filter(r -> orderId.equals(r.getOrderId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for order " + orderId));
    }


    // ------------------------------------------------------------------
    // A. Per-order Delivered checkbox (auto-save)
    // ------------------------------------------------------------------

    @Test
    void markingOneOrderDeliveredPersistsOnTheSharedOrderRow() {
        Order order = place(buyerA, 2, PaymentStatus.PENDING, OrderStatus.ORDERED);

        OrderDto updated = sellerApp.updateDeliveryStatus(seller, order.getId(), DeliveryStatus.DELIVERED);

        assertThat(updated.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(updated.getDeliveredAt()).as("the backend clock stamps the hand-off").isNotNull();
        assertThat(storedStatus(order.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(orders.findById(order.getId()).orElseThrow().getDeliveryStatus())
                .as("no legacy NULL: the flag is persisted, not derived")
                .isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(orders.findById(order.getId()).orElseThrow().getDeliveryUpdatedBy().getId())
                .as("the acting seller is recorded for audit")
                .isEqualTo(seller.getId());
    }

    @Test
    void reDeliveringNeverRewritesTheOriginalHandOffTime() {
        Order order = place(buyerA, 2, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        LocalDateTime first = sellerApp.updateDeliveryStatus(seller, order.getId(), DeliveryStatus.DELIVERED)
                .getDeliveredAt();
        LocalDateTime firstStored = orders.findById(order.getId()).orElseThrow().getDeliveredAt();
        LocalDateTime second = sellerApp.updateDeliveryStatus(seller, order.getId(), DeliveryStatus.DELIVERED)
                .getDeliveredAt();

        // The in-memory response of the first write carries nanosecond precision
        // while the persisted column is microsecond precision (H2 may round the
        // value at that boundary), so the only stable comparison is between the
        // two PERSISTED reads. A duplicate request must leave the original
        // hand-off time - and the identity of the first write - untouched.
        assertThat(first).as("the first delivery stamps the hand-off time").isNotNull();
        assertThat(second).as("a duplicate request is a no-op, not a second record")
                .isEqualTo(firstStored);
        assertThat(orders.findById(order.getId()).orElseThrow().getDeliveredAt())
                .as("the stored hand-off time is never rewritten")
                .isEqualTo(firstStored);
    }

    @Test
    void unMarkingClearsTheHandOffTime() {
        Order order = place(buyerA, 2, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        sellerApp.updateDeliveryStatus(seller, order.getId(), DeliveryStatus.DELIVERED);

        OrderDto reverted = sellerApp.updateDeliveryStatus(seller, order.getId(), DeliveryStatus.NOT_DELIVERED);

        assertThat(reverted.getDeliveryStatus()).isEqualTo("NOT_DELIVERED");
        assertThat(reverted.getDeliveredAt())
                .as("an unmarked row must not keep claiming a hand-off happened")
                .isNull();
    }

    @Test
    @DisplayName("delivery is an independent axis: it never writes money or order status")
    void deliveringNeverTouchesPaymentStatusOrOrderStatus() {
        Order unpaid = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);
        Order paid = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        sellerApp.updateDeliveryStatus(seller, unpaid.getId(), DeliveryStatus.DELIVERED);
        sellerApp.updateDeliveryStatus(seller, paid.getId(), DeliveryStatus.NOT_DELIVERED);

        Order reloadedUnpaid = orders.findById(unpaid.getId()).orElseThrow();
        assertThat(reloadedUnpaid.getPaymentStatus())
                .as("delivering an unpaid order must not make it paid")
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(reloadedUnpaid.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(reloadedUnpaid.isDelivered()).isTrue();

        Order reloadedPaid = orders.findById(paid.getId()).orElseThrow();
        assertThat(reloadedPaid.getPaymentStatus())
                .as("un-delivering a paid order must not touch the payment")
                .isEqualTo(PaymentStatus.PAID);
        assertThat(reloadedPaid.isDelivered()).isFalse();
    }

    @Test
    void theBuyerReadsTheSameDeliveryStateBackThroughTheirOwnOrders() {
        Order order = place(buyerA, 2, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        sellerApp.updateDeliveryStatus(seller, order.getId(), DeliveryStatus.DELIVERED);

        Map<String, List<OrderDto>> myOrders = orderService.getMyOrders(buyerA);
        OrderDto listed = myOrders.get("active").stream()
                .filter(o -> order.getId().equals(o.getId())).findFirst().orElseThrow();
        assertThat(listed.getDeliveryStatus())
                .as("the buyer's list badge reads the SAME Order row the seller wrote")
                .isEqualTo("DELIVERED");
        assertThat(listed.getDeliveredAt()).isNotNull();

        OrderDto detail = orderService.getOrderDetails(order.getId(), buyerA);
        assertThat(detail.getDeliveryStatus()).isEqualTo("DELIVERED");

        // And an honestly not-delivered order does not pretend otherwise.
        Order other = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);
        OrderDto otherDetail = orderService.getOrderDetails(other.getId(), buyerA);
        assertThat(otherDetail.getDeliveryStatus()).isEqualTo("NOT_DELIVERED");
        assertThat(otherDetail.getDeliveredAt()).isNull();
    }


    // ------------------------------------------------------------------
    // B. Ownership, validation, and orders that are out of play
    // ------------------------------------------------------------------

    @Test
    void aSellerCannotDeliverAnotherSellersOrder() {
        Order foreign = place(buyerB, otherPoha, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> sellerApp.updateDeliveryStatus(seller, foreign.getId(), DeliveryStatus.DELIVERED))
                .isInstanceOf(SellerNotAuthorizedException.class);
        assertThat(storedStatus(foreign.getId()))
                .as("the refused write must not leak into another kitchen's order")
                .isEqualTo(DeliveryStatus.NOT_DELIVERED);
    }

    @Test
    void aTamperedUnknownOrderIdIsNotFound() {
        assertThatThrownBy(() -> sellerApp.updateDeliveryStatus(seller, 999_999_999L, DeliveryStatus.DELIVERED))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void anUnknownOfferingIsNotFoundAndAnotherSellersOfferingIsRefused() {
        assertThatThrownBy(() -> progress(seller, newProductIdStub()))
                .isInstanceOf(com.example.my_first_spring_api.exception.ProductNotFoundException.class);

        assertThatThrownBy(() -> sellerApp.markAllOfferingOrdersDelivered(
                seller, otherPoha.getId(), LocalDate.now()))
                .isInstanceOf(SellerNotAuthorizedException.class);
    }

    @Test
    void aMissingDeliveryStatusIsRejected() {
        Order order = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> sellerApp.updateDeliveryStatus(seller, order.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        assertThat(storedStatus(order.getId())).isEqualTo(DeliveryStatus.NOT_DELIVERED);
    }

    @Test
    void aCancelledOrderCannotBeMarkedDelivered() {
        Order cancelled = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.CANCELLED);

        assertThatThrownBy(() -> sellerApp.updateDeliveryStatus(seller, cancelled.getId(), DeliveryStatus.DELIVERED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelled");
        assertThat(storedStatus(cancelled.getId()))
                .as("a cancelled order is not a delivery at all")
                .isEqualTo(DeliveryStatus.NOT_DELIVERED);
    }

    @Test
    void anUnplacedDraftBasketCannotBeMarkedDelivered() {
        Order draft = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.DRAFT);

        assertThatThrownBy(() -> sellerApp.updateDeliveryStatus(seller, draft.getId(), DeliveryStatus.DELIVERED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an active customer order");
        assertThat(storedStatus(draft.getId())).isEqualTo(DeliveryStatus.NOT_DELIVERED);
    }

    /** An id that no offering uses, for the not-found path. */
    private Product newProductIdStub() {
        Product stub = new Product();
        stub.setId(999_999_999L);
        return stub;
    }


    // ------------------------------------------------------------------
    // C. Mark All Delivered (bulk, per offering per date)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("bulk applies to active-and-not-yet-delivered rows only, and reports the real work")
    void markAllDeliversEveryActiveUndeliveredOrderOfTheOffering() {
        Order first = place(buyerA, 2, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order second = place(buyerB, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);
        Order already = place(buyerA, 3, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order cancelled = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.CANCELLED);
        Order draft = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.DRAFT);
        sellerApp.updateDeliveryStatus(seller, already.getId(), DeliveryStatus.DELIVERED);
        LocalDateTime originalHandOff = orderService.getOrderDetails(already.getId(), buyerA).getDeliveredAt();

        DeliveryProgressDto applied = sellerApp.markAllOfferingOrdersDelivered(
                seller, poha.getId(), LocalDate.now());

        assertThat(applied.getAppliedCount()).as("exactly the two rows the seller confirmed").isEqualTo(2);
        assertThat(applied.getActiveOrderCount()).as("active rows include the already-delivered one").isEqualTo(3);
        assertThat(applied.getDeliveredCount()).isEqualTo(3);
        assertThat(applied.getRemainingCount()).isZero();

        assertThat(storedStatus(first.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(storedStatus(second.getId()))
                .as("an unpaid order is deliverable: payment and delivery are independent")
                .isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(storedStatus(cancelled.getId()))
                .as("a cancelled order is skipped, not delivered")
                .isEqualTo(DeliveryStatus.NOT_DELIVERED);
        assertThat(storedStatus(draft.getId()))
                .as("an unplaced draft basket is skipped")
                .isEqualTo(DeliveryStatus.NOT_DELIVERED);
        assertThat(orders.findById(already.getId()).orElseThrow().getDeliveredAt())
                .as("the earlier hand-off time is preserved, never rewritten")
                .isEqualTo(originalHandOff);
    }

    @Test
    @DisplayName("bulk scope is the offering + date, never the seller's current filters")
    void markAllIgnoresFiltersAndCoversBothSocietiesAndBothPaymentStates() {
        Order paidA = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order pendingA = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);
        Order paidB = place(buyerB, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order pendingB = place(buyerB, 1, PaymentStatus.WILL_PAY_LATER, OrderStatus.ORDERED);

        DeliveryProgressDto preview = detail(SOCIETY_A, "paid", "not_delivered").getDeliveryProgress();
        assertThat(preview.getBulkScopeOrderCount())
                .as("the confirmation count describes the action's whole scope, not the filtered view")
                .isEqualTo(4);

        DeliveryProgressDto applied = sellerApp.markAllOfferingOrdersDelivered(
                seller, poha.getId(), LocalDate.now());

        assertThat(applied.getAppliedCount()).isEqualTo(4);
        for (Order o : List.of(paidA, pendingA, paidB, pendingB)) {
            assertThat(storedStatus(o.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        }
    }

    @Test
    void markAllIsScopedToOneDate() {
        Order today = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order yesterday = onDay(place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED),
                LocalDate.now().minusDays(1));

        DeliveryProgressDto applied = sellerApp.markAllOfferingOrdersDelivered(
                seller, poha.getId(), LocalDate.now());

        assertThat(applied.getAppliedCount()).isEqualTo(1);
        assertThat(storedStatus(today.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(storedStatus(yesterday.getId()))
                .as("another day's orders are a different delivery run")
                .isEqualTo(DeliveryStatus.NOT_DELIVERED);
    }


    @Test
    void markAllIsScopedToOneOfferingEvenInsideTheSameKitchen() {
        Order forPoha = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order forMasala = place(buyerA, masala, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order forOtherKitchen = place(buyerB, otherPoha, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        DeliveryProgressDto applied = sellerApp.markAllOfferingOrdersDelivered(
                seller, poha.getId(), LocalDate.now());

        assertThat(applied.getAppliedCount()).isEqualTo(1);
        assertThat(storedStatus(forPoha.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(storedStatus(forMasala.getId()))
                .as("a sibling offering in the same kitchen is a separate run")
                .isEqualTo(DeliveryStatus.NOT_DELIVERED);
        assertThat(storedStatus(forOtherKitchen.getId()))
                .as("another kitchen's orders are never even loaded")
                .isEqualTo(DeliveryStatus.NOT_DELIVERED);
    }

    @Test
    @DisplayName("a repeated bulk run is a no-op and reports zero applied")
    void reRunningMarkAllChangesNothing() {
        Order order = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        DeliveryProgressDto firstRun = sellerApp.markAllOfferingOrdersDelivered(
                seller, poha.getId(), LocalDate.now());
        LocalDateTime handOff = orders.findById(order.getId()).orElseThrow().getDeliveredAt();
        DeliveryProgressDto secondRun = sellerApp.markAllOfferingOrdersDelivered(
                seller, poha.getId(), LocalDate.now());

        assertThat(firstRun.getAppliedCount()).isEqualTo(1);
        assertThat(secondRun.getAppliedCount())
                .as("a second tab / double click must not claim work it did not do")
                .isZero();
        assertThat(secondRun.getRemainingCount()).isZero();
        assertThat(orders.findById(order.getId()).orElseThrow().getDeliveredAt()).isEqualTo(handOff);
    }

    @Test
    void markAllOnADayWithNoOrdersIsAnHonestNoOp() {
        DeliveryProgressDto applied = sellerApp.markAllOfferingOrdersDelivered(
                seller, poha.getId(), LocalDate.now());

        assertThat(applied.getAppliedCount()).isZero();
        assertThat(applied.getActiveOrderCount()).isZero();
        assertThat(applied.getRemainingCount()).isZero();
    }


    // ------------------------------------------------------------------
    // D. Delivery progress + the drill-down rows the seller screen renders
    // ------------------------------------------------------------------

    @Test
    void progressCountsActiveOrdersOnlyAndSplitsRemainingFromDelivered() {
        Order one = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);
        Order third = place(buyerB, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.CANCELLED);
        place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.DRAFT);

        DeliveryProgressDto start = progress(poha);
        assertThat(start.getProductId()).isEqualTo(poha.getId());
        assertThat(start.getDate()).isEqualTo(LocalDate.now());
        assertThat(start.getActiveOrderCount()).as("cancelled and draft are not deliveries").isEqualTo(3);
        assertThat(start.getDeliveredCount()).isZero();
        assertThat(start.getRemainingCount()).isEqualTo(3);
        assertThat(start.getBulkScopeOrderCount()).isEqualTo(3);

        sellerApp.updateDeliveryStatus(seller, one.getId(), DeliveryStatus.DELIVERED);
        DeliveryProgressDto mid = progress(poha);
        assertThat(mid.getDeliveredCount()).isEqualTo(1);
        assertThat(mid.getRemainingCount()).isEqualTo(2);
        assertThat(mid.getBulkScopeOrderCount()).as("only the not-yet-delivered rows remain in scope").isEqualTo(2);

        sellerApp.updateDeliveryStatus(seller, third.getId(), DeliveryStatus.DELIVERED);
        DeliveryProgressDto end = progress(poha);
        assertThat(end.getDeliveredCount()).isEqualTo(2);
        assertThat(end.getRemainingCount()).isEqualTo(1);
        assertThat(end.getAppliedCount()).as("a progress read is not a write").isZero();

        sellerApp.markAllOfferingOrdersDelivered(seller, poha.getId(), LocalDate.now());
        DeliveryProgressDto allDone = progress(poha);
        assertThat(allDone.getRemainingCount()).as("\"All deliveries completed\"").isZero();
        assertThat(allDone.getBulkScopeOrderCount()).isZero();
    }

    @Test
    void drillDownRowsCarryTheDeliveryStateAndEditability() {
        Order active = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order delivered = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);
        Order cancelled = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.CANCELLED);
        sellerApp.updateDeliveryStatus(seller, delivered.getId(), DeliveryStatus.DELIVERED);

        OrderItemDetailDto dto = detail(null, null, null);

        OrderItemDetailDto.CustomerOrderRow activeRow = rowFor(dto, active.getId());
        assertThat(activeRow.getDeliveryStatus()).isEqualTo("NOT_DELIVERED");
        assertThat(activeRow.isDelivered()).isFalse();
        assertThat(activeRow.getDeliveredAt()).isNull();
        assertThat(activeRow.isDeliveryEditable()).as("an active order can be ticked").isTrue();

        OrderItemDetailDto.CustomerOrderRow deliveredRow = rowFor(dto, delivered.getId());
        assertThat(deliveredRow.isDelivered()).isTrue();
        assertThat(deliveredRow.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(deliveredRow.getDeliveredAt()).isNotNull();
        assertThat(deliveredRow.isPaid())
                .as("delivered while still unpaid: the two axes stay independent")
                .isFalse();
        assertThat(deliveredRow.isDeliveryEditable()).isTrue();

        OrderItemDetailDto.CustomerOrderRow cancelledRow = rowFor(dto, cancelled.getId());
        assertThat(cancelledRow.isDelivered()).isFalse();
        assertThat(cancelledRow.isDeliveryEditable())
                .as("a cancelled row takes no part in delivery tracking")
                .isFalse();
        assertThat(cancelledRow.getDeliveredAt()).isNull();
    }


    @Test
    @DisplayName("the Delivery filter is a third, independent axis that combines with society and payment")
    void deliveryFilterSelectsIndependentlyAndCombinesWithTheOtherTwo() {
        Order delivered = place(buyerA, 1, PaymentStatus.WILL_PAY_LATER, OrderStatus.CONFIRMED);
        Order notDelivered = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);
        Order deliveredOtherSociety = place(buyerB, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order cancelled = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.CANCELLED);
        sellerApp.updateDeliveryStatus(seller, delivered.getId(), DeliveryStatus.DELIVERED);
        sellerApp.updateDeliveryStatus(seller, deliveredOtherSociety.getId(), DeliveryStatus.DELIVERED);

        // Delivery alone.
        assertThat(detail(null, null, "delivered").getCustomers())
                .extracting(OrderItemDetailDto.CustomerOrderRow::getOrderId)
                .containsExactlyInAnyOrder(delivered.getId(), deliveredOtherSociety.getId());
        assertThat(detail(null, null, "not_delivered").getCustomers())
                .extracting(OrderItemDetailDto.CustomerOrderRow::getOrderId)
                .containsExactly(notDelivered.getId());

        // Delivery + society.
        assertThat(detail(SOCIETY_A, null, "delivered").getCustomers())
                .extracting(OrderItemDetailDto.CustomerOrderRow::getOrderId)
                .containsExactly(delivered.getId());

        // Delivery + payment. The delivered order above is unpaid, so it belongs
        // in the unpaid bucket even though it is Delivered.
        assertThat(detail(null, "pending", "delivered").getCustomers())
                .extracting(OrderItemDetailDto.CustomerOrderRow::getOrderId)
                .containsExactly(delivered.getId());
        assertThat(detail(null, "pending", "not_delivered").getCustomers())
                .extracting(OrderItemDetailDto.CustomerOrderRow::getOrderId)
                .containsExactly(notDelivered.getId());

        // A cancelled order matches NEITHER delivery bucket, so the two buckets
        // always partition the active rows exactly.
        assertThat(detail(null, null, "delivered").getCustomers())
                .extracting(OrderItemDetailDto.CustomerOrderRow::getOrderId)
                .doesNotContain(cancelled.getId());
        assertThat(detail(null, null, "not_delivered").getCustomers())
                .extracting(OrderItemDetailDto.CustomerOrderRow::getOrderId)
                .doesNotContain(cancelled.getId());

        // The progress block is deliberately NOT filtered: it keeps describing
        // the whole offering even while the rows below are filtered.
        assertThat(detail(SOCIETY_A, "paid", "delivered").getDeliveryProgress().getActiveOrderCount())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("filtering never mutates persisted delivery state")
    void filteringIsReadOnly() {
        Order order = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        sellerApp.updateDeliveryStatus(seller, order.getId(), DeliveryStatus.DELIVERED);

        detail(null, null, "not_delivered");
        detail(SOCIETY_A, "paid", "delivered");
        detail(null, "cancelled", null);

        assertThat(storedStatus(order.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(progress(poha).getDeliveredCount()).isEqualTo(1);
    }


    // ------------------------------------------------------------------
    // E. Kitchen and Homemade Product sellers share one implementation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a Homemade Product seller's order tracks delivery exactly like a Kitchen's")
    void homemadeSellersShareTheSameDeliveryImplementation() {
        Order homemade = place(buyerA, pickle, 2, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order kitchenOrder = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        OrderDto updated = sellerApp.updateDeliveryStatus(homemadeSeller, homemade.getId(), DeliveryStatus.DELIVERED);

        assertThat(updated.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(updated.getDeliveredAt()).isNotNull();
        assertThat(storedStatus(homemade.getId())).isEqualTo(DeliveryStatus.DELIVERED);

        DeliveryProgressDto homemadeProgress = progress(homemadeSeller, pickle);
        assertThat(homemadeProgress.getActiveOrderCount()).isEqualTo(1);
        assertThat(homemadeProgress.getDeliveredCount()).isEqualTo(1);
        assertThat(homemadeProgress.getRemainingCount()).isZero();
        assertThat(homemadeProgress.getProductId()).isEqualTo(pickle.getId());

        // Cross-tenant isolation works identically for the homemade seller.
        assertThatThrownBy(() -> sellerApp.updateDeliveryStatus(homemadeSeller, kitchenOrder.getId(),
                DeliveryStatus.DELIVERED))
                .isInstanceOf(SellerNotAuthorizedException.class);
        assertThat(storedStatus(kitchenOrder.getId())).isEqualTo(DeliveryStatus.NOT_DELIVERED);

        // And the homemade bulk path behaves like the kitchen one.
        Order second = place(buyerA, pickle, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);
        DeliveryProgressDto applied = sellerApp.markAllOfferingOrdersDelivered(
                homemadeSeller, pickle.getId(), LocalDate.now());
        assertThat(applied.getAppliedCount()).isEqualTo(1);
        assertThat(storedStatus(second.getId())).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void aHomemadeSellerSeesTheDrillDownDeliveryColumnOnItsOwnOffering() {
        Order order = place(buyerA, pickle, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        sellerApp.updateDeliveryStatus(homemadeSeller, order.getId(), DeliveryStatus.DELIVERED);

        OrderItemDetailDto dto = sellerApp.getOrderItemDetail(
                homemadeSeller, pickle.getId(), LocalDate.now(), null, null, "delivered");

        assertThat(dto.getCustomers()).hasSize(1);
        assertThat(dto.getCustomers().get(0).getOrderId()).isEqualTo(order.getId());
        assertThat(dto.getCustomers().get(0).isDelivered()).isTrue();
        assertThat(dto.getDeliveryProgress().getDeliveredCount()).isEqualTo(1);
    }


    // ------------------------------------------------------------------
    // F. Mixed batches: the batch reports exactly what it changed
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a mixed batch delivers the active rows, skips the rest, and reports the exact count")
    void aMixedBatchIsReportedHonestly() {
        // A realistic kitchen screen after some manual ticking: active rows
        // (delivered and not), plus rows that must never be counted.
        Order notYet = place(buyerA, 2, PaymentStatus.PENDING, OrderStatus.ORDERED);
        Order alsoNotYet = place(buyerB, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order alreadyDelivered = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.READY);
        Order cancelled = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.CANCELLED);
        Order draft = place(buyerA, 1, PaymentStatus.PENDING, OrderStatus.DRAFT);
        sellerApp.updateDeliveryStatus(seller, alreadyDelivered.getId(), DeliveryStatus.DELIVERED);
        LocalDateTime originalHandOff = orders.findById(alreadyDelivered.getId()).orElseThrow().getDeliveredAt();

        DeliveryProgressDto preview = progress(poha);
        assertThat(preview.getActiveOrderCount()).isEqualTo(3);
        assertThat(preview.getDeliveredCount()).isEqualTo(1);
        assertThat(preview.getRemainingCount()).isEqualTo(2);

        DeliveryProgressDto applied = sellerApp.markAllOfferingOrdersDelivered(
                seller, poha.getId(), LocalDate.now());

        // Applied count is never a guess: it equals the scope measured before the write.
        assertThat(applied.getAppliedCount())
                .as("success message and dialog count can never disagree")
                .isEqualTo(preview.getBulkScopeOrderCount());
        assertThat(applied.getRemainingCount()).isZero();
        assertThat(storedStatus(notYet.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(storedStatus(alsoNotYet.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(storedStatus(cancelled.getId())).isEqualTo(DeliveryStatus.NOT_DELIVERED);
        assertThat(storedStatus(draft.getId())).isEqualTo(DeliveryStatus.NOT_DELIVERED);
        assertThat(orders.findById(alreadyDelivered.getId()).orElseThrow().getDeliveredAt())
                .isEqualTo(originalHandOff);
    }

    @Test
    void deliveryStateSurvivesRereadsFromEverySurface() {
        Order order = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        sellerApp.updateDeliveryStatus(seller, order.getId(), DeliveryStatus.DELIVERED);

        // Every read surface must agree on the SAME persisted flag.
        assertThat(storedStatus(order.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(orderService.getOrderDetails(order.getId(), buyerA).getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(orderService.getMyOrders(buyerA).get("active").get(0).getDeliveryStatus())
                .isEqualTo("DELIVERED");
        assertThat(progress(poha).getDeliveredCount()).isEqualTo(1);
        assertThat(rowFor(detail(null, null, null), order.getId()).isDelivered()).isTrue();
    }

    // ------------------------------------------------------------------
    // G. A seller with MORE THAN ONE storefront
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a seller's second storefront has its own delivery progress and bulk scope")
    void aSecondStorefrontIsNotConfusedWithTheFirst() {
        // Same seller, two storefronts of different categories - exactly the
        // Kitchen + Homemade pairing the model supports.
        Kitchen second = kitchen(seller, "second-store-" + UUID.randomUUID().toString().substring(0, 6),
                "Kingsbury", SellerType.HOMEMADE_PRODUCTS);
        Product cake = product(second, "Cake");

        Order firstStorefront = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order secondStorefront = place(buyerB, cake, 2, PaymentStatus.PENDING, OrderStatus.ORDERED);

        // Each offering reports only its OWN storefront's orders.
        DeliveryProgressDto firstProgress = progress(poha);
        assertThat(firstProgress.getActiveOrderCount()).isEqualTo(1);

        DeliveryProgressDto secondProgress = progress(cake);
        assertThat(secondProgress.getActiveOrderCount())
                .as("the second storefront's progress must count its own order")
                .isEqualTo(1);
        assertThat(secondProgress.getBulkScopeOrderCount()).isEqualTo(1);

        // Bulk-delivering the second storefront must change the second order and
        // leave the first storefront's order untouched.
        DeliveryProgressDto applied = orderService.markAllOfferingOrdersDelivered(
                cake.getId(), LocalDate.now(), seller);
        assertThat(applied.getAppliedCount()).isEqualTo(1);
        assertThat(storedStatus(secondStorefront.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(storedStatus(firstStorefront.getId()))
                .as("a bulk action must never touch another storefront's orders")
                .isEqualTo(DeliveryStatus.NOT_DELIVERED);

        // And the drill-down screen for that offering agrees.
        assertThat(rowFor(sellerApp.getOrderItemDetail(seller, cake.getId(), LocalDate.now(),
                null, null, null), secondStorefront.getId()).isDelivered()).isTrue();
    }

    @Test
    @DisplayName("Mark All Delivered on an already-complete storefront changes nothing")
    void bulkOnAnAlreadyCompleteOfferingIsANoOp() {
        Order first = place(buyerA, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Kitchen second = kitchen(seller, "done-store-" + UUID.randomUUID().toString().substring(0, 6),
                "Kingsbury", SellerType.HOMEMADE_PRODUCTS);
        Product cake = product(second, "Cake");
        Order secondOrder = place(buyerB, cake, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        orderService.markAllOfferingOrdersDelivered(cake.getId(), LocalDate.now(), seller);
        DeliveryProgressDto secondRun = orderService.markAllOfferingOrdersDelivered(
                cake.getId(), LocalDate.now(), seller);

        assertThat(secondRun.getAppliedCount()).isZero();
        assertThat(secondRun.getRemainingCount()).isZero();
        assertThat(secondRun.getDeliveredCount()).isEqualTo(1);
        assertThat(storedStatus(secondOrder.getId())).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(storedStatus(first.getId())).isEqualTo(DeliveryStatus.NOT_DELIVERED);
    }

}
