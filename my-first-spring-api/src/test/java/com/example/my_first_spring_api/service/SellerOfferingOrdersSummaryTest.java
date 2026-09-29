package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderItemDetailDto;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for the Seller "View Orders" page data.
 *
 * The page was broken for two separate reasons: the frontend never rendered the
 * rows (covered in SellerAppScriptStructureTest), and the payload it asked for
 * carried no order count at all. These tests pin the authoritative numbers the
 * page displays, so the summary can never drift from the persisted orders.
 *
 * Semantics pinned here, taken from the existing model:
 *  - an "order" is one persisted order containing the offering (one row each),
 *    so totalOrders == paid + pending + cancelled and nothing is double counted;
 *  - CANCELLED is an ORDER lifecycle state and is never also Paid or Pending;
 *  - Paid/Pending/WILL_PAY_LATER are PAYMENT states on non-cancelled orders;
 *  - WILL_PAY_LATER is an unpaid order, so it belongs in the Pending bucket;
 *  - plates and revenue count only non-cancelled orders;
 *  - revenue uses each item's persisted transaction-time price, never the
 *    product's current price;
 *  - seller ownership is enforced server-side.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seller-offering-orders;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SellerOfferingOrdersSummaryTest {

    private static final String SOCIETY_A = "Sunshine Society";
    private static final String SOCIETY_B = "Green Valley";

    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired SellerAppService sellerApp;

    private User seller;
    private User otherSeller;
    private User buyerA;
    private User buyerB;
    private Kitchen kitchen;
    private Kitchen otherKitchen;
    private Product product;

    @BeforeEach
    void setUp() {
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        seller = approvedSeller("Seller" + s, "91" + s + "01");
        otherSeller = approvedSeller("Other" + s, "92" + s + "01");

        kitchen = new Kitchen("k" + s, "Kitchen " + s, "", null, seller);
        kitchen.setSociety(SOCIETY_A);
        kitchen.setSellerType(SellerType.KITCHEN);
        kitchen = kitchens.saveAndFlush(kitchen);

        // The second seller owns a kitchen too, so the refusal below is proven to
        // come from the offering-ownership check rather than "no kitchen".
        otherKitchen = new Kitchen("ok" + s, "Other Kitchen " + s, "", null, otherSeller);
        otherKitchen.setSociety(SOCIETY_B);
        otherKitchen = kitchens.saveAndFlush(otherKitchen);

        buyerA = buyer("BuyerA" + s, "93" + s + "01", SOCIETY_A, "A Wing", "A-402");
        buyerB = buyer("BuyerB" + s, "94" + s + "01", SOCIETY_B, "B Wing", "B-602");

        product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), "plate");
        product.setAvailableToday(true);
        product.setRemainingQuantity(50);
        product.setMaxQuantity(50);
        product = products.saveAndFlush(product);
    }

    private User approvedSeller(String name, String mobile) {
        User u = new User(name, mobile, "S-1", UserRole.SELLER);
        u.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return users.saveAndFlush(u);
    }

    private User buyer(String name, String mobile, String society, String building, String flat) {
        User u = new User(name, mobile, flat, UserRole.BUYER);
        u.setSociety(society);
        u.setBuilding(building);
        return users.saveAndFlush(u);
    }

    /**
     * Persists one order for this offering. The item keeps the given
     * transaction-time price so the historical total is not recomputed from the
     * product's current price.
     */
    private Order place(User buyer, int qty, BigDecimal unitPrice,
                         PaymentStatus payment, OrderStatus orderStatus) {
        Order o = new Order(buyer, kitchen);
        o.setOrderNumber("SM" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        o.setOrderStatus(orderStatus);
        o.setPaymentStatus(payment);
        o.addItem(new OrderItem(product, qty, unitPrice));
        o.recalculateTotal();
        return orders.saveAndFlush(o);
    }

    private OrderItemDetailDto detail(String society, String status) {
        return sellerApp.getOrderItemDetail(seller, product.getId(), LocalDate.now(), society, status);
    }

    // ------------------------------------------------------------------
    // Headline counts must be mutually exclusive and add up
    // ------------------------------------------------------------------

    @Test
    void summaryCountsAreMutuallyExclusiveAndSumToTheOrderCount() {
        place(buyerA, 3, BigDecimal.valueOf(40), PaymentStatus.PAID, OrderStatus.CONFIRMED);
        place(buyerA, 2, BigDecimal.valueOf(40), PaymentStatus.PENDING, OrderStatus.ORDERED);
        place(buyerB, 1, BigDecimal.valueOf(40), PaymentStatus.WILL_PAY_LATER, OrderStatus.ORDERED);
        place(buyerB, 4, BigDecimal.valueOf(40), PaymentStatus.PAID, OrderStatus.CANCELLED);

        OrderItemDetailDto d = detail(null, null);

        assertThat(d.getTotalOrders()).as("one row per order containing the offering").isEqualTo(4);
        assertThat(d.getPaidCount()).as("a cancelled PAID order is NOT counted as paid").isEqualTo(1);
        assertThat(d.getPendingCount()).as("PENDING and WILL_PAY_LATER share the pending bucket")
                .isEqualTo(2);
        assertThat(d.getCancelledCount()).isEqualTo(1);

        // The three buckets are mutually exclusive and exhaustive.
        assertThat(d.getPaidCount() + d.getPendingCount() + d.getCancelledCount())
                .isEqualTo(d.getTotalOrders());

        // Plates and revenue come from non-cancelled orders only (3 + 2 + 1).
        assertThat(d.getTotalPlates()).isEqualTo(6);
        assertThat(d.getTotalRevenue()).isEqualByComparingTo("240.00");
    }

    @Test
    void revenueUsesThePersistedTransactionTimePriceNotTheCurrentProductPrice() {
        // Bought at 40 while the product now costs 99.
        place(buyerA, 2, BigDecimal.valueOf(40), PaymentStatus.PAID, OrderStatus.CONFIRMED);
        product.setPrice(BigDecimal.valueOf(99));
        products.saveAndFlush(product);

        OrderItemDetailDto d = detail(null, null);

        assertThat(d.getTotalRevenue())
                .as("historical orders must not be repriced from the current menu")
                .isEqualByComparingTo("80.00");
        OrderItemDetailDto.CustomerOrderRow row = d.getCustomers().get(0);
        assertThat(row.getPricePerUnit()).isEqualByComparingTo("40.00");
        assertThat(row.getQuantity()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Filters use the SAME mapping as the summary
    // ------------------------------------------------------------------

    @Test
    void societyFilterSelectsOnlyThatSocietysOrders() {
        place(buyerA, 3, BigDecimal.valueOf(40), PaymentStatus.PAID, OrderStatus.CONFIRMED);
        place(buyerB, 2, BigDecimal.valueOf(40), PaymentStatus.PENDING, OrderStatus.ORDERED);

        OrderItemDetailDto d = detail(SOCIETY_B, null);
        assertThat(d.getCustomers()).hasSize(1);
        assertThat(d.getCustomers().get(0).getSociety()).isEqualTo(SOCIETY_B);
        assertThat(d.getFilteredTotalOrders()).isEqualTo(1);
        // The overall summary is untouched by filtering.
        assertThat(d.getTotalOrders()).isEqualTo(2);
        assertThat(d.getPaidCount() + d.getPendingCount() + d.getCancelledCount())
                .isEqualTo(d.getTotalOrders());

        // "All Societies" resets it.
        assertThat(detail(null, null).getCustomers()).hasSize(2);
    }

    @Test
    void statusFilterMatchesTheSummaryBucketsExactly() {
        place(buyerA, 3, BigDecimal.valueOf(40), PaymentStatus.PAID, OrderStatus.CONFIRMED);
        place(buyerB, 2, BigDecimal.valueOf(40), PaymentStatus.PENDING, OrderStatus.ORDERED);
        place(buyerB, 1, BigDecimal.valueOf(40), PaymentStatus.WILL_PAY_LATER, OrderStatus.ORDERED);
        place(buyerA, 4, BigDecimal.valueOf(40), PaymentStatus.PAID, OrderStatus.CANCELLED);

        assertThat(detail(null, "paid").getCustomers())
                .as("the cancelled PAID order must not appear under Paid").hasSize(1);
        assertThat(detail(null, "pending").getCustomers())
                .as("PENDING and WILL_PAY_LATER both appear under Pending").hasSize(2);
        assertThat(detail(null, "cancelled").getCustomers()).hasSize(1);
        // Bucket sizes agree with the summary counters.
        assertThat(detail(null, "paid").getCustomers().size()).isEqualTo(detail(null, null).getPaidCount());
        assertThat(detail(null, "pending").getCustomers().size()).isEqualTo(detail(null, null).getPendingCount());
        assertThat(detail(null, "cancelled").getCustomers().size()).isEqualTo(detail(null, null).getCancelledCount());
        // "All Status" resets it.
        assertThat(detail(null, "").getCustomers()).hasSize(4);
    }

    @Test
    void societyAndStatusFiltersCombine() {
        place(buyerA, 3, BigDecimal.valueOf(40), PaymentStatus.PAID, OrderStatus.CONFIRMED);
        place(buyerA, 1, BigDecimal.valueOf(40), PaymentStatus.PENDING, OrderStatus.ORDERED);
        place(buyerB, 2, BigDecimal.valueOf(40), PaymentStatus.PAID, OrderStatus.CONFIRMED);

        OrderItemDetailDto d = detail(SOCIETY_A, "paid");
        assertThat(d.getCustomers()).hasSize(1);
        assertThat(d.getCustomers().get(0).getSociety()).isEqualTo(SOCIETY_A);
        assertThat(d.getFilteredTotalOrders()).isEqualTo(1);
        assertThat(d.getFilteredPaidCount()).isEqualTo(1);
        assertThat(d.getFilteredCancelledCount()).isZero();
        // Headline summary remains the unfiltered truth.
        assertThat(d.getTotalOrders()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // Empty + ownership
    // ------------------------------------------------------------------

    @Test
    void anOfferingWithNoOrdersReportsZeroesNotAnError() {
        OrderItemDetailDto d = detail(null, null);
        assertThat(d.getTotalOrders()).isZero();
        assertThat(d.getTotalPlates()).isZero();
        assertThat(d.getTotalRevenue()).isEqualByComparingTo("0");
        assertThat(d.getPaidCount() + d.getPendingCount() + d.getCancelledCount()).isZero();
        assertThat(d.getCustomers()).isEmpty();
    }

    @Test
    void aSellerCannotReadAnotherSellersOfferingOrders() {
        place(buyerA, 3, BigDecimal.valueOf(40), PaymentStatus.PAID, OrderStatus.CONFIRMED);
        // The other seller has no kitchen of their own here, so this is refused
        // before any customer data could be read.
        assertThatThrownBy(() -> sellerApp.getOrderItemDetail(
                otherSeller, product.getId(), LocalDate.now(), null, null))
                .isInstanceOf(SellerNotAuthorizedException.class);
    }
}
