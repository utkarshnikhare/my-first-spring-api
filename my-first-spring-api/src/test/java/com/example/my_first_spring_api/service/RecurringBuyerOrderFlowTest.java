package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemDetailDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.RecurringScheduleDto;
import com.example.my_first_spring_api.dto.SellerDashboardDto;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Occurrence;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.RecurringSchedule;
import com.example.my_first_spring_api.model.OccurrenceStatus;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OccurrenceRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.RecurringScheduleRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2 acceptance: the BUYER side of the recurring engine.
 *
 * <p>Covers the requirement-3 scenarios end to end through the real services:</p>
 * <ul>
 *   <li>Order Now on a scheduled day lands on that occurrence's date (own bucket);</li>
 *   <li>a date-specific seller override is what the buyer-facing DTO shows and
 *       the server enforces (quantity cap, close/ready times);</li>
 *   <li>the per-date quantity means the whole day's plates, not one order's;</li>
 *   <li>a future occurrence can be ordered ahead as a pre-order carrying its
 *       delivery date;</li>
 *   <li>End Schedule stops further ordering while history is preserved.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recurring-buyer-flow;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class RecurringBuyerOrderFlowTest {

    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private ProductRepository products;
    @Autowired private RecurringScheduleRepository schedules;
    @Autowired private OccurrenceRepository occurrences;
    @Autowired private SellerService sellerService;
    @Autowired private RecurringScheduleService scheduleService;
    @Autowired private SellerAppService sellerApp;
    @Autowired private KitchenService kitchenService;
    @Autowired private MarketplaceService marketplaceService;
    @Autowired private OrderService orderService;

    private User seller;
    private User buyer;
    private Kitchen kitchen;

    @Autowired
    private FeatureService featureService;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        seller = new User("Seller" + suffix, "91" + suffix + "01", null, UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.saveAndFlush(seller);
        buyer = new User("Buyer" + suffix, "93" + suffix + "01", "A-1", UserRole.BUYER);
        buyer.setSociety("Society");
        buyer.setBuilding("A");
        buyer = users.saveAndFlush(buyer);
        kitchen = kitchens.saveAndFlush(new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller));

        // The "test" profile skips DataInitializer, so seed the feature
        // catalogue explicitly: future-occurrence ordering needs the same
        // "advance menus" allowance as any other ahead-dated offering.
        featureService.ensureDefaults();
        featureService.setSellerGrant(seller.getId(), FeatureService.KEY_PREORDERS, true, null);
    }

    // ---------------------------------------------------------------- helpers

    /** Creates a recurring product for one selling day (default: start today). */
    private ProductDto createRecurring(String name, LocalDate start, Integer productMax, Integer dayQty) {
        ProductCreateDto dto = new ProductCreateDto();
        dto.setName(name);
        dto.setPrice(BigDecimal.valueOf(25));
        dto.setCategories(List.of("BREAKFAST"));
        dto.setAvailableDate(start);
        dto.setMaxQuantity(productMax);
        dto.setOrderWindowEnd("23:58");
        dto.setReadyByTime(start.atTime(23, 59).toString());

        RecurringScheduleDto recurring = new RecurringScheduleDto();
        recurring.setStartDate(start);
        recurring.setEndDate(start.plusDays(14));
        recurring.setRecurrenceWeekdays(EnumSet.of(start.getDayOfWeek()));
        recurring.setDefaultQuantity(dayQty);
        recurring.setDefaultOrderCloseTime("23:58");
        recurring.setDefaultReadyByTime(start.atTime(18, 0).toString());
        dto.setRecurringSchedule(recurring);

        return sellerService.createProduct(kitchen.getId(), dto, seller);
    }

    private MockHttpSession buyerSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        return session;
    }

    private OrderItemRequest request(Long productId, int qty) {
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(productId);
        request.setQuantity(qty);
        return request;
    }

    private Occurrence occurrenceOf(Long productId, LocalDate date) {
        RecurringSchedule schedule = schedules.findByProductId(productId).orElseThrow();
        return occurrences.findByScheduleIdAndOccurrenceDate(schedule.getId(), date).orElseThrow();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void dashboardExposesRecurringContextForTheLiveCard() {
        ProductDto product = createRecurring("Poha", LocalDate.now(), null, 14);

        SellerDashboardDto dash = sellerApp.getDashboard(seller);
        ProductDto card = dash.getOfferings().stream()
                .filter(p -> p.getId().equals(product.getId())).findFirst().orElseThrow();

        assertThat(card.getRecurring()).isTrue();
        assertThat(card.getOccurrenceId()).isNotNull();
        assertThat(card.getNextOccurrenceDate()).isEqualTo(LocalDate.now());
        assertThat(card.getMaxQuantity()).isEqualTo(14); // today's resolved default
        assertThat(card.getCutoffTime()).isEqualTo("23:58");
    }

    @Test
    void todaysOverrideIsReflectedInTheBuyerFacingDto() {
        ProductDto product = createRecurring("Poha", LocalDate.now(), null, 14);
        Occurrence today = occurrenceOf(product.getId(), LocalDate.now());
        // Wednesday-style override: 25 plates, close 20:00, ready 19:00.
        scheduleService.updateOccurrenceOverride(today.getId(), 25, "20:00", "19:00", null, null, null);

        KitchenDetailDto detail = kitchenService.getKitchenDetailById(kitchen.getId(), buyer);
        ProductDto shown = detail.getProducts().stream()
                .filter(p -> p.getId().equals(product.getId())).findFirst().orElseThrow();

        assertThat(shown.getRecurring()).isTrue();
        assertThat(shown.getOccurrenceId()).isEqualTo(today.getId());
        assertThat(shown.getMaxQuantity()).isEqualTo(25);      // override wins
        assertThat(shown.getOrderWindowEnd()).isEqualTo("20:00");
        assertThat(shown.getCutoffTime()).isEqualTo("20:00");
        assertThat(shown.getReadyByTime()).isEqualTo("19:00");
        assertThat(shown.getOrdersClosed()).isFalse();

        // The schedule defaults themselves are untouched by the override.
        RecurringSchedule schedule = schedules.findByProductId(product.getId()).orElseThrow();
        assertThat(schedule.getDefaultQuantity()).isEqualTo(14);
        assertThat(schedule.getDefaultOrderCloseTime()).isEqualTo("23:58");
    }

    @Test
    void buyerOrderOnScheduledDayBucketsOnTheOccurrenceDate() {
        ProductDto product = createRecurring("Poha", LocalDate.now(), 50, 14);
        MockHttpSession session = buyerSession();

        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request(product.getId(), 3)), session);
        OrderDto placed = orderService.placeOrder(PaymentStatus.PENDING, null, null, session);

        assertThat(placed.getItems()).hasSize(1);
        assertThat(placed.getItems().get(0).getScheduledDate()).isEqualTo(LocalDate.now());
        assertThat(placed.getItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    void perDateQuantityCapIsEnforcedAcrossOrders() {
        ProductDto product = createRecurring("Poha", LocalDate.now(), 50, 5);
        MockHttpSession first = buyerSession();

        // One order may never exceed the day's plates...
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(
                kitchen.getId(), List.of(request(product.getId(), 6)), buyerSession()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At most 5");

        // ...and separate orders may not add up past them either.
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request(product.getId(), 4)), first);
        orderService.placeOrder(PaymentStatus.PENDING, null, null, first);

        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(
                kitchen.getId(), List.of(request(product.getId(), 2)), buyerSession()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("left for " + LocalDate.now());
    }

    @Test
    void futureOccurrenceCanBeOrderedAheadWithItsDate() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        ProductDto product = createRecurring("Poha", tomorrow, 50, 10);
        assertThat(products.findById(product.getId()).orElseThrow().getIsPreorder()).isFalse();

        // Visible to buyers even though its availableDate is still ahead.
        KitchenDetailDto detail = kitchenService.getKitchenDetailById(kitchen.getId(), buyer);
        assertThat(detail.getPreorderProducts()).extracting(ProductDto::getId).contains(product.getId());
        ProductDto shown = detail.getPreorderProducts().stream()
                .filter(p -> p.getId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(shown.getNextOccurrenceDate()).isEqualTo(tomorrow);
        assertThat(marketplaceService.getAllAvailableItems(buyer))
                .extracting(ProductDto::getId).contains(product.getId());

        Occurrence tomorrowOccurrence = occurrenceOf(product.getId(), tomorrow);
        scheduleService.updateOccurrenceOverride(tomorrowOccurrence.getId(), 25, "20:00", "7:00 PM",
                null, null, null);
        ProductDto overridden = kitchenService.getKitchenDetailById(kitchen.getId(), buyer).getPreorderProducts().stream()
                .filter(p -> p.getId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(overridden.getMaxQuantity()).isEqualTo(25);
        assertThat(overridden.getRemainingQuantity()).isEqualTo(25);
        assertThat(overridden.getCutoffTime()).isEqualTo("20:00");
        assertThat(overridden.getReadyByTime()).isEqualTo("7:00 PM");

        // Ordering ahead succeeds and carries the fulfilment date.
        MockHttpSession session = buyerSession();
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request(product.getId(), 2)), session);
        OrderDto placed = orderService.placeOrder(PaymentStatus.PENDING, null, null, session);
        assertThat(placed.getItems().get(0).getScheduledDate()).isEqualTo(tomorrow);
        assertThat(placed.getItems().get(0).getOccurrenceId()).isEqualTo(tomorrowOccurrence.getId());
        assertThatThrownBy(() -> scheduleService.updateOccurrenceOverride(
                tomorrowOccurrence.getId(), 1, null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already ordered");
        assertThatThrownBy(() -> scheduleService.updateOccurrenceOverride(
                tomorrowOccurrence.getId(), null, null, "8:00 PM", null, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot change");
        scheduleService.updateOccurrenceOverride(
                tomorrowOccurrence.getId(), null, "21:00", null, null, null, null);

        OrderItemDetailDto occurrenceOrders = sellerApp.getOrderItemDetail(
                seller, product.getId(), tomorrow, null, null, null);
        OrderItemDetailDto todaysOrders = sellerApp.getOrderItemDetail(
                seller, product.getId(), LocalDate.now(), null, null, null);
        assertThat(occurrenceOrders.getTotalOrders()).isEqualTo(1);
        assertThat(occurrenceOrders.getTotalPlates()).isEqualTo(2);
        assertThat(todaysOrders.getTotalOrders()).isZero();
    }

    @Test
    void buyerSelectedOccurrenceDateIsHonouredAndClosedLifecycleIsRejected() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        ProductDto product = createRecurring("Poha", tomorrow, 50, 10);
        LocalDate nextWeek = tomorrow.plusWeeks(1);
        Occurrence selected = occurrenceOf(product.getId(), nextWeek);
        OrderItemRequest request = request(product.getId(), 2);
        request.setScheduledDate(nextWeek.toString());

        MockHttpSession session = buyerSession();
        OrderDto draft = orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request), session);
        assertThat(draft.getItems().get(0).getOccurrenceId()).isEqualTo(selected.getId());
        assertThat(draft.getItems().get(0).getScheduledDate()).isEqualTo(nextWeek);

        selected.setStatus(OccurrenceStatus.ORDERS_CLOSED);
        occurrences.saveAndFlush(selected);
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(
                kitchen.getId(), List.of(request), buyerSession()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not open for orders on " + nextWeek);
    }

    @Test
    void recurringOccurrenceCapacityIsIndependentOfProductOneTimeStock() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        ProductDto product = createRecurring("Poha", tomorrow, 2, 10);
        Product stored = products.findById(product.getId()).orElseThrow();
        stored.setMaxQuantity(2);
        stored.setRemainingQuantity(2);
        products.save(stored);

        MockHttpSession session = buyerSession();
        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request(product.getId(), 6)), session);
        OrderDto placed = orderService.placeOrder(PaymentStatus.PENDING, null, null, session);

        assertThat(placed.getItems().get(0).getQuantity()).isEqualTo(6);
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(2);
        ProductDto resolved = kitchenService.getKitchenDetailById(kitchen.getId(), buyer).getPreorderProducts().stream()
                .filter(p -> p.getId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(resolved.getBookedQuantity()).isEqualTo(6);
        assertThat(resolved.getRemainingQuantity()).isEqualTo(4);
    }

    @Test
    void endedScheduleStopsAllFurtherOrderingButKeepsHistory() {
        ProductDto product = createRecurring("Poha", LocalDate.now(), 50, 14);
        RecurringSchedule schedule = schedules.findByProductId(product.getId()).orElseThrow();
        Occurrence today = occurrenceOf(product.getId(), LocalDate.now());

        scheduleService.endSchedule(schedule.getId());

        // History (the occurrence row) is preserved...
        assertThat(occurrences.findById(today.getId())).isPresent();
        // ...but no further ordering is possible.
        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(
                kitchen.getId(), List.of(request(product.getId(), 1)), buyerSession()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not currently open for orders");
        // The buyer-facing detail marks the offering closed (server + UI agree).
        KitchenDetailDto closedDetail = kitchenService.getKitchenDetailById(kitchen.getId(), buyer);
        ProductDto closedShown = closedDetail.getProducts().stream()
                .filter(p -> p.getId().equals(product.getId())).findFirst().orElse(null);
        if (closedShown != null) {
            assertThat(closedShown.getOrdersClosed()).isTrue();
        }
    }
}
