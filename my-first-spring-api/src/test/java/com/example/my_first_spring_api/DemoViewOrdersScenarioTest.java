package com.example.my_first_spring_api;

import com.example.my_first_spring_api.dto.OrderItemDetailDto;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.EnquiryRepository;
import com.example.my_first_spring_api.repository.FavouriteRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.PlatformSettingRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.SellerTemplateRepository;
import com.example.my_first_spring_api.repository.AreaRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import com.example.my_first_spring_api.service.SellerAppService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the demo scenario behind the Seller "View Orders" page.
 *
 * The reported defect: the Poha offering card advertised a booked quantity that
 * no persisted order accounted for (the seeder derived booked = max - remaining
 * at product creation and never applied the inventory rules real checkout uses),
 * and the demo only contained one tiny order per product per day, so the page
 * could not demonstrate multiple customers, societies or payment states.
 *
 * Pinned invariants:
 *  - seeding is idempotent: re-running never duplicates orders or customers and
 *    never moves inventory twice;
 *  - booked/available are DERIVED from the authoritative persisted orders under
 *    the existing rules (non-cancelled plates are booked, remaining = max - booked);
 *  - the drill-down lists every persisted order for the offering exactly once,
 *    with mutually exclusive Paid/Pending/Cancelled buckets;
 *  - society and status filters (and their combination) work on the seeded data,
 *    and the dropdown's society options stay complete while a filter is active.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo-view-orders-scenario;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoViewOrdersScenarioTest {

    private static final LocalDateTime ALL_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired PlatformSettingRepository settings;
    @Autowired OrderRepository orders;
    @Autowired EnquiryRepository enquiries;
    @Autowired FavouriteRepository favourites;
    @Autowired SellerTemplateRepository templates;
    @Autowired AreaRepository areas;
    @Autowired com.example.my_first_spring_api.repository.SocietyRepository societies;
    @Autowired SellerAppService sellerApp;

    private DemoDataSeeder seeder;
    private User aarti;
    private Kitchen kitchen;
    private Product poha;

    @BeforeAll
    void seedOnce() {
        seeder = new DemoDataSeeder(users, kitchens, products, settings, orders, enquiries, favourites, templates, areas, societies);
        seeder.seedAll();
        aarti = users.findByMobileNumber("9100000001").orElseThrow();
        kitchen = kitchens.findBySeller(aarti).get(0);
        poha = products.findByKitchen(kitchen).stream()
                .filter(p -> "Poha".equalsIgnoreCase(p.getName()))
                .findFirst().orElseThrow();
    }

    @Test
    void repeatedSeedingNeverDuplicatesOrdersCustomersOrInventory() {
        long orderCountBefore = orders.count();
        Set<String> scenarioBefore = scenarioOrderNumbers();
        int bookedBefore = refreshedPoha().getBookedQuantity();
        int remainingBefore = refreshedPoha().getRemainingQuantity();
        long buyersBefore = users.findByRole(UserRole.BUYER).size();

        assertThat(scenarioBefore)
                .as("the scenario must have created its six deterministic orders")
                .hasSize(6);
        assertThat(orderCountBefore).isGreaterThan(6);

        seeder.seedAll();
        seeder.seedAll();

        assertThat(orders.count()).as("no duplicate orders").isEqualTo(orderCountBefore);
        assertThat(scenarioOrderNumbers())
                .as("the deterministic order numbers stay unique")
                .hasSize(6);
        assertThat(users.findByRole(UserRole.BUYER).size()).isEqualTo(buyersBefore);
        Product pohaAfter = refreshedPoha();
        assertThat(pohaAfter.getBookedQuantity()).isEqualTo(bookedBefore);
        assertThat(pohaAfter.getRemainingQuantity()).isEqualTo(remainingBefore);
    }

    // ------------------------------------------------------------------
    // Inventory is derived from persisted orders, never written by hand
    // ------------------------------------------------------------------

    @Test
    void bookedAndAvailableQuantitiesAreDerivedFromThePersistedOrders() {
        Product p = refreshedPoha();

        int bookedFromOrders = 0;
        for (Order o : persistedOrders()) {
            int qty = quantityOf(p, o);
            if (qty <= 0) continue;
            if (o.getOrderStatus() == OrderStatus.CANCELLED || o.getOrderStatus() == OrderStatus.DRAFT) continue;
            bookedFromOrders += qty;
        }

        assertThat(bookedFromOrders)
                .as("the six scenario orders alone contribute 18 non-cancelled plates")
                .isGreaterThanOrEqualTo(18);
        assertThat(p.getBookedQuantity())
                .as("dashboard booked must equal the sum of non-cancelled order plates")
                .isEqualTo(bookedFromOrders);
        assertThat(p.getRemainingQuantity())
                .as("available = max - booked (existing inventory rule)")
                .isEqualTo(p.getMaxQuantity() - p.getBookedQuantity());
        assertThat(p.getRemainingQuantity()).isGreaterThanOrEqualTo(0);
    }

    // ------------------------------------------------------------------
    // View Orders lists every persisted order exactly once, with buckets
    // ------------------------------------------------------------------

    @Test
    void viewOrdersListsEveryPersistedOrderOnceWithConsistentBuckets() {
        OrderItemDetailDto d = sellerApp.getOrderItemDetail(aarti, poha.getId(), LocalDate.now(), null, null);

        int persistedToday = 0;
        BigDecimal expectedRevenue = BigDecimal.ZERO;
        for (Order o : persistedOrders()) {
            if (quantityOf(poha, o) <= 0) continue;
            if (o.getCreatedAt() == null || !o.getCreatedAt().toLocalDate().equals(LocalDate.now())) continue;
            persistedToday++;
            if (o.getOrderStatus() != OrderStatus.CANCELLED) {
                for (OrderItem item : o.getItems()) {
                    if (item.getProduct().getId().equals(poha.getId())) {
                        expectedRevenue = expectedRevenue.add(
                                item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                    }
                }
            }
        }

        assertThat(persistedToday).isGreaterThanOrEqualTo(6);
        assertThat(d.getTotalOrders())
                .as("every persisted order for today becomes exactly one row")
                .isEqualTo(persistedToday);
        assertThat(d.getCustomers()).hasSize(persistedToday);
        assertThat(d.getPaidCount() + d.getPendingCount() + d.getCancelledCount())
                .as("buckets are mutually exclusive and exhaustive")
                .isEqualTo(d.getTotalOrders());
        assertThat(d.getTotalRevenue()).isEqualByComparingTo(expectedRevenue);

        // One 6-plate order is ONE row, not six: plates never inflate the count.
        assertThat(d.getCustomers().stream().filter(c -> c.getQuantity() == 6)
                .collect(Collectors.toList())).hasSize(1);

        // The deterministic rows exist with their societies and statuses.
        assertThat(d.getCustomers().stream()
                .map(OrderItemDetailDto.CustomerOrderRow::getSociety)
                .collect(Collectors.toSet()))
                .contains("Sunshine Society", "Green Valley");

        OrderItemDetailDto.CustomerOrderRow willPayLater = rowByOrderNumber(d, "SM-712");
        assertThat(willPayLater.getQuantity()).isEqualTo(2);
        assertThat(willPayLater.isPaid()).isFalse();
        assertThat(willPayLater.isCancelled()).isFalse();

        OrderItemDetailDto.CustomerOrderRow cancelled = rowByOrderNumber(d, "SM-715");
        assertThat(cancelled.isCancelled()).isTrue();

        assertThat(d.getPendingCount()).as("PENDING + WILL_PAY_LATER rows for today").isGreaterThanOrEqualTo(2);
        assertThat(d.getCancelledCount()).isGreaterThanOrEqualTo(1);
        assertThat(d.getPaidCount()).isGreaterThanOrEqualTo(3);
    }

    // ------------------------------------------------------------------
    // Filters work against the seeded data; summary stays unfiltered
    // ------------------------------------------------------------------

    @Test
    void societyAndStatusFiltersWorkOnTheSeededScenario() {
        OrderItemDetailDto all = sellerApp.getOrderItemDetail(aarti, poha.getId(), LocalDate.now(), null, null);

        OrderItemDetailDto greenValley = sellerApp.getOrderItemDetail(
                aarti, poha.getId(), LocalDate.now(), "Green Valley", null);
        assertThat(greenValley.getCustomers()).isNotEmpty();
        assertThat(greenValley.getCustomers())
                .allMatch(c -> "Green Valley".equals(c.getSociety()));
        assertThat(greenValley.getFilteredTotalOrders()).isEqualTo(greenValley.getCustomers().size());
        assertThat(greenValley.getTotalOrders())
                .as("the headline summary never changes with the filters")
                .isEqualTo(all.getTotalOrders());

        OrderItemDetailDto pending = sellerApp.getOrderItemDetail(
                aarti, poha.getId(), LocalDate.now(), null, "pending");
        assertThat(pending.getCustomers()).isNotEmpty();
        assertThat(pending.getCustomers()).allMatch(c -> !c.isPaid() && !c.isCancelled());

        OrderItemDetailDto cancelled = sellerApp.getOrderItemDetail(
                aarti, poha.getId(), LocalDate.now(), null, "cancelled");
        assertThat(cancelled.getCustomers()).isNotEmpty();
        assertThat(cancelled.getCustomers()).allMatch(OrderItemDetailDto.CustomerOrderRow::isCancelled);

        // Combined filter: only cancelled orders of the selected society.
        OrderItemDetailDto combo = sellerApp.getOrderItemDetail(
                aarti, poha.getId(), LocalDate.now(), "Green Valley", "cancelled");
        assertThat(combo.getCustomers()).isNotEmpty();
        assertThat(combo.getCustomers())
                .allMatch(c -> "Green Valley".equals(c.getSociety()) && c.isCancelled());

        // Clearing both filters restores every row.
        assertThat(sellerApp.getOrderItemDetail(aarti, poha.getId(), LocalDate.now(), "", "")
                .getCustomers()).hasSize(all.getTotalOrders());

        // Dropdown options come from the UNFILTERED set: both societies stay
        // listed even while the Green Valley filter is applied.
        assertThat(all.getAvailableSocieties()).contains("Sunshine Society", "Green Valley");
        assertThat(greenValley.getAvailableSocieties()).contains("Sunshine Society", "Green Valley");
    }

    // ------------------------------------------------------------------
    // Booked inventory vs the date-scoped rows: both are correct, so the
    // drill-down carries the dashboard figure to explain the difference
    // ------------------------------------------------------------------

    @Test
    void drillDownCarriesTheBookedFigureSoBothScreensAgreeOnMeaning() {
        OrderItemDetailDto today = sellerApp.getOrderItemDetail(aarti, poha.getId(), LocalDate.now(), null, null);
        Product p = refreshedPoha();

        assertThat(today.getDashboardBookedQuantity())
                .as("the drill-down reports exactly the booked figure the dashboard card shows")
                .isEqualTo(p.getBookedQuantity());
        assertThat(today.getDashboardBookedQuantity())
                .as("booked spans every date the offering is posted for, so it can never sit below one date's plates")
                .isGreaterThanOrEqualTo(today.getTotalPlates());
        assertThat(today.getProductUnit())
                .as("the wording follows the offering's own unit")
                .isEqualTo(p.getPriceUnit());

        // Whatever the difference is, it must be fully accounted for by plates
        // booked on dates other than the one being viewed - never by a lost order.
        int platesOnOtherDates = 0;
        for (Order o : persistedOrders()) {
            if (o.getOrderStatus() == OrderStatus.CANCELLED) continue;
            if (o.getCreatedAt() == null || o.getCreatedAt().toLocalDate().equals(LocalDate.now())) continue;
            platesOnOtherDates += quantityOf(poha, o);
        }
        assertThat(today.getDashboardBookedQuantity() - today.getTotalPlates())
                .as("booked minus this date's plates equals the plates booked on the other dates")
                .isEqualTo(platesOnOtherDates);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Product refreshedPoha() {
        return products.findById(poha.getId()).orElseThrow();
    }

    private List<Order> persistedOrders() {
        return orders.findByKitchenAndCreatedAtAfterWithItems(kitchen, ALL_TIME);
    }

    private int quantityOf(Product product, Order order) {
        int qty = 0;
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null && item.getProduct().getId().equals(product.getId())) {
                qty += item.getQuantity() != null ? item.getQuantity() : 0;
            }
        }
        return qty;
    }

    private Set<String> scenarioOrderNumbers() {
        return orders.findAll().stream()
                .map(Order::getOrderNumber)
                .filter(n -> n != null && n.startsWith("SM-71"))
                .collect(Collectors.toSet());
    }

    private OrderItemDetailDto.CustomerOrderRow rowByOrderNumber(OrderItemDetailDto d, String orderNumber) {
        return d.getCustomers().stream()
                .filter(c -> orderNumber.equals(c.getOrderNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing order row " + orderNumber));
    }
}
