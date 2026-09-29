package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.SellerOrderSummaryDto;
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

/**
 * Regression tests for the Orders screen daily summary (Screen 7A).
 *
 * Two defects were fixed here:
 *  1. WILL_PAY_LATER orders fell into NO bucket (neither PAID nor PENDING), so
 *     Paid + Pending + Cancelled added up to less than the total order count;
 *  2. per-offering counters incremented once per order ITEM, so an order with
 *     the same offering twice was double-counted as two orders.
 *
 * Pinned semantics: PAID -> Paid; PENDING and WILL_PAY_LATER -> Pending;
 * CANCELLED (order lifecycle) -> Cancelled, never also Paid/Pending; revenue
 * counts only non-cancelled orders at transaction-time prices; plates and order
 * counts are never inflated by multi-item orders.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seller-order-summary-buckets;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SellerOrderSummaryBucketsTest {

    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired SellerAppService sellerApp;

    private User seller;
    private User buyer;
    private Kitchen kitchen;
    private Product poha;

    @BeforeEach
    void setUp() {
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        seller = new User("Seller" + s, "91" + s + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.saveAndFlush(seller);
        buyer = new User("Buyer" + s, "93" + s + "01", "A-1", UserRole.BUYER);
        buyer.setSociety("Sunshine Society");
        buyer = users.saveAndFlush(buyer);
        kitchen = new Kitchen("k" + s, "Kitchen " + s, "", null, seller);
        kitchen.setSellerType(SellerType.KITCHEN);
        kitchen = kitchens.saveAndFlush(kitchen);
        poha = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), "plate");
        poha.setAvailableToday(true);
        poha.setMaxQuantity(50);
        poha.setRemainingQuantity(50);
        poha = products.saveAndFlush(poha);
    }

    private Order place(int qty, PaymentStatus payment, OrderStatus status) {
        Order o = new Order(buyer, kitchen);
        o.setOrderNumber("SM" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        o.setOrderStatus(status);
        o.setPaymentStatus(payment);
        o.addItem(new OrderItem(poha, qty, BigDecimal.valueOf(40)));
        o.recalculateTotal();
        return orders.saveAndFlush(o);
    }

    private SellerOrderSummaryDto summary() {
        return sellerApp.getOrderSummary(seller, LocalDate.now());
    }

    private SellerOrderSummaryDto.ProductOrderAggregate pohaAgg(SellerOrderSummaryDto s) {
        return s.getProducts().stream()
                .filter(p -> p.getProductId().equals(poha.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing Poha aggregate"));
    }

    @Test
    void willPayLaterCountsAsPendingAndTheBucketsAlwaysSumToTheTotal() {
        place(3, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        place(2, PaymentStatus.PENDING, OrderStatus.ORDERED);
        place(1, PaymentStatus.WILL_PAY_LATER, OrderStatus.ORDERED);
        place(4, PaymentStatus.PAID, OrderStatus.CANCELLED);

        SellerOrderSummaryDto s = summary();

        assertThat(s.getTotalOrderCount()).isEqualTo(4);
        assertThat(s.getPaidCount()).as("only non-cancelled PAID orders").isEqualTo(1);
        assertThat(s.getPendingCount())
                .as("PENDING and WILL_PAY_LATER are both pending payment")
                .isEqualTo(2);
        assertThat(s.getCancelledCount()).isEqualTo(1);
        assertThat(s.getPaidCount() + s.getPendingCount() + s.getCancelledCount())
                .as("the three buckets are mutually exclusive and exhaustive")
                .isEqualTo(s.getTotalOrderCount());
        // Revenue: non-cancelled only, at transaction-time price (3+2+1)*40.
        assertThat(s.getTotalRevenue()).isEqualByComparingTo("240.00");

        SellerOrderSummaryDto.ProductOrderAggregate agg = pohaAgg(s);
        assertThat(agg.getTotalOrders()).isEqualTo(3);
        assertThat(agg.getPaidCount() + agg.getPendingCount()).isEqualTo(agg.getTotalOrders());
        assertThat(agg.getPendingCount()).isEqualTo(2);
        assertThat(agg.getTotalPlates()).isEqualTo(6);
    }

    @Test
    void oneOrderWithTwoItemsOfTheSameOfferingCountsAsOneOrder() {
        // One order carrying the offering as TWO items (2 + 3 plates)...
        Order twoItems = new Order(buyer, kitchen);
        twoItems.setOrderNumber("SM" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        twoItems.setOrderStatus(OrderStatus.CONFIRMED);
        twoItems.setPaymentStatus(PaymentStatus.PAID);
        twoItems.addItem(new OrderItem(poha, 2, BigDecimal.valueOf(40)));
        twoItems.addItem(new OrderItem(poha, 3, BigDecimal.valueOf(40)));
        twoItems.recalculateTotal();
        orders.saveAndFlush(twoItems);
        // ...and a second order with a single item.
        place(1, PaymentStatus.PENDING, OrderStatus.ORDERED);

        SellerOrderSummaryDto s = summary();

        assertThat(s.getTotalOrderCount()).isEqualTo(2);
        SellerOrderSummaryDto.ProductOrderAggregate agg = pohaAgg(s);
        assertThat(agg.getTotalOrders())
                .as("two items of one order must NOT count as two orders")
                .isEqualTo(2);
        assertThat(agg.getTotalPlates()).isEqualTo(6);
        assertThat(agg.getPaidCount()).isEqualTo(1);
        assertThat(agg.getPendingCount()).isEqualTo(1);
        assertThat(agg.getRevenue()).isEqualByComparingTo("240.00");
    }

    @Test
    void cancelledOrdersAreExcludedFromRevenueAndProductAggregates() {
        place(5, PaymentStatus.PAID, OrderStatus.CANCELLED);

        SellerOrderSummaryDto s = summary();

        assertThat(s.getTotalOrderCount()).isEqualTo(1);
        assertThat(s.getCancelledCount()).isEqualTo(1);
        assertThat(s.getPaidCount())
                .as("a cancelled PAID order is never also Paid")
                .isZero();
        assertThat(s.getPendingCount()).isZero();
        assertThat(s.getTotalRevenue()).isEqualByComparingTo("0");
        assertThat(s.getProducts())
                .as("cancelled orders never feed the offering aggregates")
                .isEmpty();
    }

    @Test
    void aDayWithNoOrdersReportsAllZeroes() {
        SellerOrderSummaryDto s = summary();

        assertThat(s.getTotalOrderCount()).isZero();
        assertThat(s.getPaidCount()).isZero();
        assertThat(s.getPendingCount()).isZero();
        assertThat(s.getCancelledCount()).isZero();
        assertThat(s.getTotalRevenue()).isEqualByComparingTo("0");
        assertThat(s.getProducts()).isEmpty();
    }
}
