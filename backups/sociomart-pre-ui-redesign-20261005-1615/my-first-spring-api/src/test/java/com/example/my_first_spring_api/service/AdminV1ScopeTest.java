package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.DeliveryStatus;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.SellerType;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.AreaRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.SocietyRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin V1 - the eight-section operational console.
 *
 * <p>Covers only what V1 changed, and pins what must not drift:</p>
 * <ul>
 *   <li>navigation is the approved eight sections, and the developer tools
 *       (Diagnose / Health / Console) plus redundant sections (Kitchens, Items,
 *       Enquiries) are no longer primary navigation;</li>
 *   <li>the money figure is "Recorded Order Value", never "Revenue" - SocioMart
 *       does not process buyer payments;</li>
 *   <li>Orders monitoring filters compose with AND, and the delivery axis reads
 *       the SAME shared Order flag the Seller Delivery tracker writes, so Admin
 *       can never become a second delivery system;</li>
 *   <li>Pending Actions is derived from real state, never invented;</li>
 *   <li>exports carry a header row, a generated-at timestamp, and honour the
 *       same filters as the screen they mirror.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-v1;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class AdminV1ScopeTest {

    @Autowired AdminService adminService;
    @Autowired OrderRepository orders;
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired AreaRepository areas;
    @Autowired SocietyRepository societies;

    private static String adminJs;
    private static String adminHtml;

    private User seller;
    private User homemadeSeller;
    private User buyerInSociety;
    private User buyerElsewhere;
    private Kitchen kitchen;
    private Kitchen homemadeKitchen;
    private Product kitchenItem;
    private Product homemadeItem;
    private Area area;
    private Society society;

    @BeforeEach
    void setUp() throws IOException {
        // The H2 instance is shared across the class (DB_CLOSE_DELAY=-1), so every
        // test starts from a clean slate. Otherwise fixtures from earlier tests
        // would leak into later ones and make count assertions meaningless.
        orders.deleteAll();
        products.deleteAll();
        kitchens.deleteAll();
        users.deleteAll();
        societies.deleteAll();
        areas.deleteAll();

        adminJs = read("js/admin.js");
        adminHtml = read("admin.html");
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        seller = approvedSeller("Seller" + s, "91" + s + "01");
        homemadeSeller = approvedSeller("Homemade" + s, "92" + s + "01");
        buyerInSociety = buyer("Buyer" + s, "93" + s + "01", "A-1");
        buyerElsewhere = buyer("Outsider" + s, "94" + s + "01", "B-9");

        area = areas.save(new Area("Area " + s));
        society = societies.save(new Society(area, "Society " + s));

        kitchen = kitchen(seller, "k" + s, SellerType.KITCHEN);
        homemadeKitchen = kitchen(homemadeSeller, "h" + s, SellerType.HOMEMADE_PRODUCTS);
        kitchenItem = product(kitchen, "Kitchen Dish " + s);
        homemadeItem = product(homemadeKitchen, "Homemade Jar " + s);

        // Buyers resolve location through the STABLE references, which is exactly
        // what the Admin Area/Society filters match on.
        buyerInSociety.setAreaRef(area);
        buyerInSociety.setSocietyRef(society);
        users.saveAndFlush(buyerInSociety);
    }

    // ---- A. Navigation is the approved eight sections -----------------

    @Test
    @DisplayName("primary navigation is exactly the eight Admin V1 sections")
    void navigationIsTheApprovedEightSections() {
        for (String label : List.of("Dashboard", "Approvals", "Sellers", "Buyers",
                "Orders", "Analytics", "Areas &amp; Societies", "Exports")) {
            assertThat(adminHtml).as("V1 section must be in the nav: " + label)
                    .contains("<span class=\"nav-label\">" + label + "</span>");
        }
        long items = adminHtml.lines().filter(l -> l.contains("class=\"nav-item")).count();
        assertThat(items).as("Admin V1 has exactly eight primary nav items").isEqualTo(8L);
    }

    @Test
    @DisplayName("developer tools and redundant sections are not primary navigation")
    void developerToolsAreNotPrimaryNavigation() {
        String nav = adminHtml.substring(adminHtml.indexOf("id=\"adminNav\""),
                adminHtml.indexOf("</nav>"));
        for (String gone : List.of("Diagnose", "Health", "Console", "Kitchens",
                ">Items<", "Enquiries", ">Areas<")) {
            assertThat(nav).as("must not be primary Admin navigation: " + gone).doesNotContain(gone);
        }
        // The routes still resolve, so an existing bookmark or developer link is
        // not broken by the UI simplification.
        for (String route : List.of("'#/diagnostics'", "'#/health'", "'#/console'",
                "'#/kitchens'", "'#/offerings'", "'#/enquiries'")) {
            assertThat(adminJs).as("route retained: " + route).contains(route);
        }
    }

    @Test
    @DisplayName("renamed sections keep the old hashes working and highlighted")
    void legacyHashesStillResolve() {
        assertThat(adminJs).contains("'#/home': adminHomeView")
                .contains("'#/pending': adminPendingView")
                .contains("if (key === 'home') key = 'dashboard';")
                .contains("if (key === 'pending') key = 'approvals';");
    }


    // ---- B. Dashboard wording ----------------------------------------

    @Test
    @DisplayName("the money metric is Recorded Order Value, not Revenue")
    void theMoneyMetricIsNotCalledRevenue() {
        String dashboard = dashboardSource();
        assertThat(dashboard).contains("'Recorded Order Value'");
        // Check the RENDERED label, not the word: the source deliberately contains a
        // comment explaining why the metric is NOT called Revenue, so a raw
        // doesNotContain on the whole view would match that comment.
        assertThat(dashboard).as("no visible tile or heading may say Revenue")
                .doesNotContain("'Revenue'")
                .doesNotContain(">Revenue<")
                .doesNotContain("'Platform Revenue'")
                .doesNotContain("'SocioMart Revenue'");
        assertThat(adminJs).doesNotContain("Platform Revenue").doesNotContain("SocioMart Revenue");
    }

    @Test
    @DisplayName("dashboard drops the redundant tiles and reports real active users")
    void dashboardTilesAreFocusedAndHonest() {
        String dashboard = dashboardSource();
        assertThat(dashboard)
                .doesNotContain("'Favourites',")
                .doesNotContain("'Offerings',")
                .doesNotContain("'Kitchens',")
                .doesNotContain("'Enquiries',");
        // Handover 4/17 names the exact cards the dashboard must carry:
        //   Traffic | Orders | Recorded Order Value | Pending Approvals
        //   Buyers | Sellers | Attention Needed
        assertThat(dashboard).contains("'Traffic'")
                .contains("'Orders'")
                .contains("'Recorded Order Value'")
                .contains("'Pending Approvals'")
                .contains("'Buyers'")
                .contains("'Sellers'")
                .contains("'Attention Needed'")
                .contains("Recent Orders")
                .contains("Pending Actions")
                // Handover 4/17: Today / Last 5 Days / Custom, server-resolved.
                .contains("Last 5 Days")
                .contains("adminDashCustomDate")
                .contains("/api/admin/dashboard?date=");
        // "Revenue" is the one word the handover forbids for this figure.
        assertThat(dashboard).doesNotContain("'Revenue'");
        assertThat(adminService.dashboard()).containsKeys("activeBuyersToday",
                "activeSellersToday", "totalOrderValue", "pendingSellers");
        // Traffic and Attention Needed are real counters, not decoration.
        assertThat(adminService.dashboard()).containsKeys("marketplaceViewsInPeriod",
                "storefrontViewsInPeriod", "offeringViewsInPeriod", "attentionNeeded");
        // The window figures exist alongside the fixed-semantics headline cards,
        // so no existing consumer of /dashboard changes meaning.
        assertThat(adminService.dashboard()).containsKeys("selectedPeriod",
                "ordersInPeriod", "recordedOrderValueInPeriod", "buyersInPeriod", "sellersInPeriod");
    }

    @Test
    @DisplayName("dashboard date selector scopes orders and recorded value to the window")
    void dashboardWindowScopesTheFigures() {
        LocalDate today = LocalDate.now();
        place(buyerInSociety, kitchenItem, 2, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        Map<String, Object> todays = adminService.dashboard("today");
        assertThat(todays.get("selectedPeriod")).isEqualTo("Today");
        long todayOrders = ((Number) todays.get("ordersInPeriod")).longValue();
        assertThat(todayOrders).isGreaterThanOrEqualTo(1L);

        // Last 5 Days is a superset of Today, so its order count can never be
        // smaller - that is the property that makes the selector trustworthy.
        Map<String, Object> last5 = adminService.dashboard("last5");
        assertThat(last5.get("selectedPeriod")).isEqualTo("Last 5 Days");
        long last5Orders = ((Number) last5.get("ordersInPeriod")).longValue();
        assertThat(last5Orders).isGreaterThanOrEqualTo(todayOrders);

        // A day before the order was placed must not count it.
        Map<String, Object> empty = adminService.dashboard(today.minusDays(3).toString());
        assertThat(((Number) empty.get("ordersInPeriod")).longValue()).isZero();

        // An unparseable value degrades to Today rather than blanking the screen.
        assertThat(adminService.dashboard("not-a-date").get("selectedPeriod")).isEqualTo("Today");
    }

    @Test
    void activeUsersAreCountedFromRealOrders() {
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Map<String, Object> d = adminService.dashboard();
        assertThat(d.get("activeBuyersToday")).isEqualTo(1L);
        assertThat(d.get("activeSellersToday")).isEqualTo(1L);
    }


    // ---- C. Orders monitoring filters -------------------------------

    @Test
    @DisplayName("payment and delivery are independent axes that compose")
    void paymentAndDeliveryCompose() {
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED, DeliveryStatus.DELIVERED);
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PENDING, OrderStatus.ORDERED, DeliveryStatus.DELIVERED);
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);

        assertThat(ids(filter().payment("PAID").delivery("delivered"))).hasSize(1);
        assertThat(ids(filter().payment("PAID").delivery("not_delivered"))).hasSize(1);
        assertThat(ids(filter().payment("PENDING").delivery("delivered")))
                .as("delivering an UNPAID order must stay legal").hasSize(1);
        assertThat(ids(filter().payment("PENDING").delivery("not_delivered"))).hasSize(1);
    }

    @Test
    @DisplayName("the delivery filter reads the shared Order flag, not a derived value")
    void deliveryFilterUsesTheSharedDeliveryFlag() {
        Order delivered = place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID,
                OrderStatus.CONFIRMED, DeliveryStatus.DELIVERED);
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        Map<String, Object> row = adminService.orders(filter().delivery("delivered")).get(0);
        assertThat(row.get("deliveryStatus")).isEqualTo("DELIVERED");
        assertThat(row.get("deliveredAt")).as("Admin reads the seller's own hand-off time").isNotNull();
        assertThat(row.get("id")).isEqualTo(delivered.getId());
    }

    @Test
    @DisplayName("a cancelled order is not an active delivery and Admin never writes one")
    void cancelledOrdersAreNotDeliveredAndNothingIsWritten() {
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CANCELLED, DeliveryStatus.DELIVERED);
        Order active = place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        assertThat(ids(filter().delivery("delivered"))).as("a cancelled order is not a delivery").isEmpty();
        assertThat(ids(filter().delivery("not_delivered")))
                .as("...and it is not in the 'still to deliver' bucket either")
                .containsExactly(active.getId());

        Map<String, Object> activeRow = adminService.orders(filter()).stream()
                .filter(r -> active.getId().equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(activeRow.get("deliveryEditable")).isEqualTo(true);
        for (Map<String, Object> row : adminService.orders(filter())) {
            if (active.getId().equals(row.get("id"))) continue;
            assertThat(row.get("deliveryEditable"))
                    .as("a cancelled row offers no delivery control").isEqualTo(false);
        }

        // Reading the Admin screen must not change any stored delivery state.
        assertThat(orders.findAll()).allSatisfy(o ->
                assertThat(o.getEffectiveDeliveryStatus()).isNotNull());
    }

    @Test
    @DisplayName("category distinguishes Kitchen from Homemade Products")
    void categoryFilterUsesTheExistingSellerType() {
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        place(buyerInSociety, homemadeItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        assertThat(ids(filter().category("KITCHEN"))).hasSize(1);
        assertThat(ids(filter().category("HOMEMADE_PRODUCTS"))).hasSize(1);
        assertThat(adminService.orders(filter()))
                .anySatisfy(r -> assertThat(r).containsEntry("category", "KITCHEN"))
                .anySatisfy(r -> assertThat(r).containsEntry("category", "HOMEMADE_PRODUCTS"));
    }

    @Test
    @DisplayName("seller, buyer and order-status filters all narrow correctly")
    void sellerBuyerAndStatusFilters() {
        Order mine = place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order theirs = place(buyerElsewhere, homemadeItem, 1, PaymentStatus.PAID, OrderStatus.ORDERED);

        assertThat(ids(filter().sellerId(seller.getId()))).containsExactly(mine.getId());
        assertThat(ids(filter().sellerId(homemadeSeller.getId()))).containsExactly(theirs.getId());
        assertThat(ids(filter().buyerId(buyerInSociety.getId()))).containsExactly(mine.getId());
        assertThat(ids(filter().status("ORDERED"))).containsExactly(theirs.getId());
        assertThat(ids(filter().status("CONFIRMED"))).containsExactly(mine.getId());
    }


    @Test
    @DisplayName("Area and Society filter on stable IDs, not free text")
    void areaAndSocietyFilterOnStableIds() {
        Order inside = place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        Order outside = place(buyerElsewhere, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        assertThat(ids(filter().societyId(society.getId()))).containsExactly(inside.getId());
        assertThat(ids(filter().areaId(area.getId())))
                .as("an area filter returns the buyers inside it").containsExactly(inside.getId());
        assertThat(ids(filter().areaId(-1L)))
                .as("an unknown area matches nothing rather than everything").isEmpty();
        assertThat(ids(filter())).containsExactlyInAnyOrder(inside.getId(), outside.getId());
    }

    @Test
    @DisplayName("every axis combines; no filter is silently dropped")
    void allAxesCombine() {
        Order match = place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED,
                DeliveryStatus.DELIVERED);
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PENDING, OrderStatus.CONFIRMED, DeliveryStatus.DELIVERED);
        place(buyerElsewhere, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED, DeliveryStatus.DELIVERED);
        place(buyerInSociety, homemadeItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED, DeliveryStatus.DELIVERED);

        assertThat(ids(filter().societyId(society.getId()).sellerId(seller.getId())
                .category("KITCHEN").payment("PAID").delivery("delivered").status("CONFIRMED")))
                .as("every axis must be applied together, not just the last one")
                .containsExactly(match.getId());
    }

    @Test
    @DisplayName("a blank or unknown axis imposes no constraint instead of matching nothing")
    void blankAxisIsNotAMatchNothingAxis() {
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        assertThat(ids(filter().payment(""))).hasSize(1);
        assertThat(ids(filter().delivery(null))).hasSize(1);
        assertThat(ids(filter().payment("ALL"))).hasSize(1);
        assertThat(ids(filter().category("ALL"))).hasSize(1);
        assertThat(ids(filter().payment("NOT_A_STATUS"))).hasSize(1);
        assertThat(ids(filter().date("not-a-date"))).hasSize(1);
    }

    @Test
    void aDateFilterMatchesOnlyThatDay() {
        Order today = place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        assertThat(ids(filter().date(LocalDate.now().toString()))).containsExactly(today.getId());
        assertThat(ids(filter().date(LocalDate.now().minusDays(3).toString()))).isEmpty();
    }

    // ---- D. Buyer inspection -----------------------------------------

    @Test
    void buyerInspectionExposesSupportFacts() {
        place(buyerInSociety, kitchenItem, 2, PaymentStatus.PAID, OrderStatus.CONFIRMED,
                DeliveryStatus.DELIVERED);

        assertThat(buyerRow(buyerInSociety)).containsEntry("orderCount", 1)
                .containsEntry("deliveredCount", 1L)
                .containsEntry("paidCount", 1L)
                .containsEntry("societyId", society.getId())
                .containsEntry("areaId", area.getId())
                .containsEntry("accountStatus", "PROFILE_COMPLETE");
    }

    @Test
    @DisplayName("account status reports the real stored block state, never an invented one")
    void accountStatusReportsTheStoredBlockFlag() {
        // Handover 8 added a REAL buyer block to the domain (User.blocked /
        // blockedReason / blockedAt), so the Buyers row now reports account state
        // truthfully. Before any block the flag is false - a stored fact, not a
        // fabricated one - and after an Admin block the row reflects it.
        assertThat(buyerRow(buyerElsewhere)).containsEntry("accountStatus", "PROFILE_INCOMPLETE")
                .containsEntry("blocked", false);
        assertThat(buyerRow(buyerElsewhere).get("blockedReason")).isNull();

        User blocked = buyerElsewhere;
        blocked.setBlocked(true);
        blocked.setBlockedReason("Repeated complaints");
        blocked.setBlockedAt(LocalDateTime.now());
        users.saveAndFlush(blocked);

        assertThat(buyerRow(blocked)).containsEntry("blocked", true)
                .containsEntry("blockedReason", "Repeated complaints");
    }


    // ---- E. Pending Actions -------------------------------------------

    @Test
    void pendingActionsAreDerivedFromRealState() {
        User waiting = new User("Waiting",
                "97" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "01",
                "S-9", UserRole.SELLER);
        waiting.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        users.saveAndFlush(waiting);
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PENDING, OrderStatus.ORDERED);

        List<Map<String, Object>> attention = adminService.attentionItems();
        assertThat(attention).isNotEmpty();
        assertThat(attention).allSatisfy(a -> assertThat(a).containsKeys("label", "count", "hash"));
        assertThat(attention.stream().map(a -> a.get("label")).toList())
                .contains("Seller applications awaiting approval")
                .contains("Orders with payment still pending");
        assertThat(attention).allSatisfy(a ->
                assertThat(String.valueOf(a.get("hash"))).matches("#/(approvals|sellers|orders)"));
    }

    @Test
    void aQuietPlatformShowsNoPendingActions() {
        assertThat(adminService.attentionItems())
                .as("nothing pending -> empty panel, not a fabricated item").isEmpty();
    }

    // ---- F. Exports ---------------------------------------------------

    @Test
    void everyExportCarriesAHeaderAndAGeneratedAtStamp() {
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);
        for (String domain : List.of("orders", "sellers", "buyers", "analytics")) {
            String csv = adminService.exportCsv(domain, filter());
            assertThat(csv).as(domain + " export must be timestamped").contains("# Generated at ");
            assertThat(csv).as(domain + " export must carry a header row").contains(",");
        }
        assertThat(adminService.exportCsv("orders", filter()))
                .as("the orders export names the money column correctly")
                .contains("Recorded Order Value").doesNotContain("Revenue");
    }

    @Test
    @DisplayName("an export honours the same filters as the screen it mirrors")
    void exportRespectsTheFilters() {
        Order delivered = place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID,
                OrderStatus.CONFIRMED, DeliveryStatus.DELIVERED);
        place(buyerInSociety, kitchenItem, 1, PaymentStatus.PAID, OrderStatus.CONFIRMED);

        String csv = adminService.exportCsv("orders", filter().delivery("delivered"));
        assertThat(csv).contains(String.valueOf(delivered.getId()))
                .contains("# Filters applied: delivery=delivered");
        assertThat(csv.lines().filter(l -> l.startsWith(String.valueOf(delivered.getId()))).toList())
                .as("exactly the filtered rows, no more").hasSize(1);
    }

    @Test
    void csvEscapingSurvivesCommasInData() {
        User buyer = buyer("Comma, Buyer",
                "95" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "01", "A-2");
        Order o = new Order(buyer, kitchen);
        o.setOrderNumber("CM-1");
        o.setOrderStatus(OrderStatus.CONFIRMED);
        o.setPaymentStatus(PaymentStatus.PAID);
        o.addItem(new OrderItem(kitchenItem, 1, new BigDecimal("40")));
        o.recalculateTotal();
        orders.saveAndFlush(o);

        assertThat(adminService.exportCsv("orders", filter()))
                .as("a value containing a comma must be quoted, not split the row")
                .contains("\"Comma, Buyer\"");
    }

    // ---- G. Areas & Societies stay one working master ------------------

    @Test
    void areasAndSocietiesRemainOneWorkingMaster() {
        assertThat(adminService.locations()).containsKeys("areas", "areaCount", "societyCount");
        assertThat(adminHtml).as("one combined section, not two").contains("Areas &amp; Societies");
    }

    @Test
    @DisplayName("creating a society does not auto-assign existing sellers")
    void creatingASocietyDoesNotAssignSellers() {
        Map<String, Object> created = adminService.createSociety(area.getId(),
                "Fresh Society " + UUID.randomUUID());
        Long newId = Long.valueOf(String.valueOf(created.get("id")));

        // Use the no-arg overload: it is the transactional one, so the lazy seller
        // and coverage relationships resolve. (The String overload is not
        // transactional - pre-existing, and not worth changing for a demoted screen.)
        Map<String, Object> kitchenRow = adminService.kitchens().stream()
                .filter(k -> kitchen.getId().equals(k.get("id")))
                .findFirst().orElseThrow();
        List<?> served = (List<?>) kitchenRow.get("servedSocietyIds");
        assertThat(served.stream().map(String::valueOf).toList())
                .as("seller coverage stays an explicit seller choice")
                .doesNotContain(String.valueOf(newId));
    }


    // ---- Helpers ------------------------------------------------------

    /** Fluent filter builder so each test reads as a list of ANDed axes. */
    private static final class OrderFilterBuilder extends AdminService.OrderFilter {
        OrderFilterBuilder payment(String v) { this.payment = v; return this; }
        OrderFilterBuilder delivery(String v) { this.delivery = v; return this; }
        OrderFilterBuilder category(String v) { this.category = v; return this; }
        OrderFilterBuilder status(String v) { this.status = v; return this; }
        OrderFilterBuilder date(String v) { this.date = v; return this; }
        OrderFilterBuilder sellerId(Long v) { this.sellerId = v; return this; }
        OrderFilterBuilder buyerId(Long v) { this.buyerId = v; return this; }
        OrderFilterBuilder areaId(Long v) { this.areaId = v; return this; }
        OrderFilterBuilder societyId(Long v) { this.societyId = v; return this; }
    }

    private OrderFilterBuilder filter() { return new OrderFilterBuilder(); }

    private List<Long> ids(AdminService.OrderFilter f) {
        return adminService.orders(f).stream().map(r -> (Long) r.get("id")).toList();
    }

    private Map<String, Object> buyerRow(User buyer) {
        return adminService.buyers().stream()
                .filter(b -> buyer.getId().equals(b.get("id")))
                .findFirst().orElseThrow();
    }

    /** The adminHomeView body only - stops at whichever view function follows it. */
    private String dashboardSource() {
        int start = adminJs.indexOf("async function adminHomeView(");
        assertThat(start).as("adminHomeView must exist").isGreaterThanOrEqualTo(0);
        int end = Integer.MAX_VALUE;
        for (String next : List.of("async function adminPendingView(", "async function adminExportsView(")) {
            int at = adminJs.indexOf(next, start);
            if (at > start) end = Math.min(end, at);
        }
        assertThat(end).as("a view function must follow adminHomeView").isLessThan(Integer.MAX_VALUE);
        return adminJs.substring(start, end);
    }

    private User approvedSeller(String name, String mobile) {
        User u = new User(name, mobile, "S-1", UserRole.SELLER);
        u.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return users.saveAndFlush(u);
    }

    private User buyer(String name, String mobile, String flat) {
        return users.saveAndFlush(new User(name, mobile, flat, UserRole.BUYER));
    }

    private Kitchen kitchen(User owner, String slug, SellerType type) {
        Kitchen k = new Kitchen(slug, "Kitchen " + slug, "", null, owner);
        k.setSociety("Sunshine Society");
        k.setSellerType(type);
        return kitchens.saveAndFlush(k);
    }

    private Product product(Kitchen owner, String name) {
        Product p = new Product(owner, name, "desc", new BigDecimal("40"), "plate");
        p.setAvailableToday(true);
        p.setRemainingQuantity(50);
        p.setMaxQuantity(50);
        return products.saveAndFlush(p);
    }

    private Order place(User buyer, Product item, int qty, PaymentStatus pay, OrderStatus status) {
        return place(buyer, item, qty, pay, status, DeliveryStatus.NOT_DELIVERED);
    }

    /**
     * Places a real order and, when asked, records delivery through the SAME
     * entity API the Seller tracker uses - so these tests prove integration with
     * the existing delivery implementation, not a copy of it.
     */
    private Order place(User buyer, Product item, int qty, PaymentStatus pay, OrderStatus status,
                        DeliveryStatus delivery) {
        Order o = new Order(buyer, item.getKitchen());
        o.setOrderNumber("AV" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        o.setOrderStatus(status);
        o.setPaymentStatus(pay);
        o.addItem(new OrderItem(item, qty, new BigDecimal("40")));
        o.recalculateTotal();
        o = orders.saveAndFlush(o);
        if (delivery == DeliveryStatus.DELIVERED) {
            o.applyDeliveryStatus(DeliveryStatus.DELIVERED, seller, LocalDateTime.now());
            o = orders.saveAndFlush(o);
        }
        return o;
    }

    private static String read(String relative) throws IOException {
        Path onDisk = Path.of("src", "main", "resources", "static",
                relative.replace('/', java.io.File.separatorChar));
        return Files.readString(onDisk, StandardCharsets.UTF_8);
    }
}

