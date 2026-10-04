package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Admin handover clauses that had no implementation: buyer search by
 * order reference (s8), location usage counts and the "sellers opt in" rule (s11),
 * Area/Society enable-disable auditing (s14), the blocked-buyer / paused-storefront
 * attention rows (s13) and the dashboard date selector (s4).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-gaps;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class AdminHandoverGapTest {

    @Autowired private AdminService adminService;
    @Autowired private LocationService locationService;
    @Autowired private AdminAuditLogRepository auditRepo;
    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private OrderService orderService;
    @Autowired private AnalyticsEventRepository analyticsEventRepo;

    private static int seq = 0;
    private final String sfx = "gap" + (seq++);

    private User admin() {
        return users.findByMobileNumber("9000000001").orElseGet(() ->
                users.save(new User("Admin " + sfx, "9000000001", "A-1", UserRole.SUPER_ADMIN)));
    }

    /** Unique mobile per call: JUnit may reuse one test instance's field, so the
     *  counter - not the per-test suffix - guarantees a fresh number each time. */
    private static int mobileSeq = 0;

    private User buyer() {
        return users.save(new User("Buyer " + sfx + mobileSeq, "93" + String.format("%06d", mobileSeq++),
                "B-1", UserRole.BUYER));
    }

    record Seller(User user, Kitchen kitchen, Product product) {}

    private Seller sellerWithStore(String name) {
        User s = new User(name + sfx, "92" + Math.abs((name + sfx).hashCode() % 100000), "K-1", UserRole.SELLER);
        s.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        users.save(s);
        Kitchen k = kitchens.save(new Kitchen("k" + name + sfx, "Store " + name + sfx, "", null, s));
        Product p = new Product(k, "Dish " + name + sfx, "", BigDecimal.valueOf(40), null);
        p.setAvailableToday(true);
        p.setMaxQuantity(20);
        p.setRemainingQuantity(20);
        products.save(p);
        return new Seller(s, k, p);
    }

    private Order place(User b, Kitchen k, Product p, String suffix, int qty) {
        return place(b, k, p, suffix, qty, PaymentStatus.PAID);
    }

    /**
     * Payment is a parameter because a seller-side "Mark as Paid" is a no-op on
     * an order that is already paid - a test for that transition needs an order
     * that genuinely starts unpaid.
     */
    private Order place(User b, Kitchen k, Product p, String suffix, int qty, PaymentStatus payment) {
        Order o = new Order(b, k);
        o.setOrderNumber("ORD-" + sfx + suffix);
        o.setOrderStatus(OrderStatus.CONFIRMED);
        o.setPaymentStatus(payment);
        o.addItem(new OrderItem(p, qty, BigDecimal.valueOf(40)));
        o.recalculateTotal();
        return orders.save(o);
    }

    // ---------- Section 8: buyer search ----------

    @Test
    @DisplayName("s8: buyer search finds a buyer from an order number or order id")
    void buyerSearchMatchesOrderReference() {
        Seller s = sellerWithStore("Srch");
        User target = buyer();
        target.setName("Searchable Buyer " + sfx);
        users.save(target);
        Order placed = place(target, s.kitchen(), s.product(), "s1", 1);

        assertThat(adminService.buyers(placed.getOrderNumber(), null, null))
                .extracting(m -> m.get("id")).contains(target.getId());
        assertThat(adminService.buyers(String.valueOf(placed.getId()), null, null))
                .extracting(m -> m.get("id")).contains(target.getId());

        // A DIFFERENT buyer's order reference must not drag this buyer in.
        User other = buyer();
        other.setName("Unrelated Buyer " + sfx);
        users.save(other);
        Order otherOrder = place(other, s.kitchen(), s.product(), "s2", 1);
        assertThat(adminService.buyers(otherOrder.getOrderNumber(), null, null))
                .extracting(m -> m.get("id")).doesNotContain(target.getId());
    }

    @Test
    @DisplayName("s8: buyer search matches name/mobile; an empty term lists every buyer")
    void buyerSearchMatchesProfileFields() {
        sellerWithStore("Prof");
        User b = buyer();
        b.setName("Distinctive Name " + sfx);
        b.setMobileNumber("94" + Math.abs(sfx.hashCode() % 100000));
        users.save(b);

        assertThat(adminService.buyers("Distinctive Name " + sfx, null, null))
                .extracting(m -> m.get("id")).contains(b.getId());
        assertThat(adminService.buyers(b.getMobileNumber(), null, null))
                .extracting(m -> m.get("id")).contains(b.getId());
        // No criteria returns the whole list, never "everything matching ''".
        assertThat(adminService.buyers(null, null, null))
                .hasSameSizeAs(users.findByRole(UserRole.BUYER));
    }

    // ---------- Section 11: usage counts and the opt-in rule ----------

    @Test
    @DisplayName("s11: locations expose buyer/seller usage counts from stable ids")
    void locationsReportUsageCounts() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> areas =
                (List<Map<String, Object>>) adminService.locations().get("areas");
        assertThat(areas).isNotEmpty();

        Map<String, Object> anyArea = areas.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> societies = (List<Map<String, Object>>) anyArea.get("societies");
        Map<String, Object> anySociety = societies.get(0);

        assertThat(anySociety).containsKeys("buyerCount", "sellerCount");
        assertThat(((Number) anySociety.get("buyerCount")).longValue()).isNotNegative();
        assertThat(((Number) anySociety.get("sellerCount")).longValue()).isNotNegative();
        // The Area figure is the sum of the rows rendered underneath it.
        assertThat(((Number) anyArea.get("buyerCount")).longValue())
                .isEqualTo(societies.stream()
                        .mapToLong(r -> ((Number) r.get("buyerCount")).longValue()).sum());
    }

    @Test
    @DisplayName("s11: a new society is served by nobody until a seller opts in")
    void newSocietyIsNotAutoServed() {
        Seller s = sellerWithStore("Opt");
        Area area = locationService.createArea("OptArea " + sfx);
        Society fresh = locationService.createSociety(area.getId(), "Fresh Society " + sfx);

        Map<String, Object> row = societyRowFor(fresh.getId());
        assertThat(row).isNotNull();
        assertThat(((Number) row.get("sellerCount")).longValue())
                .as("handover 11: sellers opt in, they never inherit a new society").isZero();
        assertThat(s.kitchen().getServedSocieties())
                .as("creating a society must not attach it to any storefront")
                .noneMatch(sc -> fresh.getId().equals(sc.getId()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> societyRowFor(Long societyId) {
        for (Map<String, Object> area : (List<Map<String, Object>>) adminService.locations().get("areas")) {
            for (Map<String, Object> soc : (List<Map<String, Object>>) area.get("societies")) {
                if (societyId.equals(soc.get("id"))) return soc;
            }
        }
        return null;
    }

    // ---------- Section 14: Area/Society auditing ----------

    @Test
    @DisplayName("s14: enabling/disabling an Area or Society writes an audit row")
    void locationChangesAreAudited() {
        User a = admin();
        Area area = locationService.createArea("Aud " + sfx);
        Society soc = locationService.createSociety(area.getId(), "AudSoc " + sfx);

        // Society first: LocationService refuses to disable an Area that still
        // has active societies, and that existing guard must keep winning.
        adminService.updateSociety(soc.getId(), null, false, a);
        assertThat(adminService.updateArea(area.getId(), null, false, a).get("active")).isEqualTo(false);
        assertThat(adminService.updateArea(area.getId(), null, true, a).get("active")).isEqualTo(true);
        assertThat(adminService.updateSociety(soc.getId(), null, true, a).get("active")).isEqualTo(true);

        assertThat(auditRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("AREA", area.getId()))
                .extracting(AdminAuditLog::getAction)
                .contains(AdminAuditService.AREA_DISABLED, AdminAuditService.AREA_ENABLED);
        assertThat(auditRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("SOCIETY", soc.getId()))
                .extracting(AdminAuditLog::getAction)
                .contains(AdminAuditService.SOCIETY_DISABLED, AdminAuditService.SOCIETY_ENABLED);

        AdminAuditLog disable = auditRepo
                .findByTargetTypeAndTargetIdOrderByCreatedAtDesc("AREA", area.getId()).stream()
                .filter(l -> AdminAuditService.AREA_DISABLED.equals(l.getAction())).findFirst().orElseThrow();
        assertThat(disable.getActorId()).isEqualTo(a.getId());
        assertThat(disable.getOldState()).isEqualTo("ACTIVE");
        assertThat(disable.getNewState()).isEqualTo("DISABLED");
        assertThat(disable.getReason()).isNotBlank();
    }

    @Test
    @DisplayName("s14: a rename or a no-op PATCH writes no enable/disable audit row")
    void renameAloneIsNotAudited() {
        User a = admin();
        Area area = locationService.createArea("Quiet " + sfx);

        adminService.updateArea(area.getId(), "Quiet Renamed " + sfx, null, a);
        adminService.updateArea(area.getId(), null, null, a);

        assertThat(auditRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("AREA", area.getId()))
                .as("only real state changes are audited, not every click").isEmpty();
    }

    // ---------- Section 13: attention ----------

    @Test
    @DisplayName("s13: attention surfaces blocked buyers and paused storefronts")
    void attentionCoversBlockedBuyersAndPausedStorefronts() {
        Seller s = sellerWithStore("Att");
        User b = buyer();
        users.save(b);
        adminService.blockBuyer(b.getId(), "repeated complaints", admin());
        adminService.pauseStorefront(s.kitchen().getId(), "stock investigation", admin());

        List<String> labels = adminService.attentionItems().stream()
                .map(m -> String.valueOf(m.get("label"))).toList();
        assertThat(labels).contains("Blocked buyers", "Paused storefronts");

        // Every attention row must link somewhere the operator can act.
        assertThat(adminService.attentionItems())
                .allSatisfy(m -> assertThat(m.get("hash")).asString().startsWith("#/"));
    }

    @Test
    @DisplayName("s13: attention rows always carry a real count")
    void attentionCountsAreReal() {
        assertThat(adminService.attentionItems())
                .allSatisfy(m -> assertThat(((Number) m.get("count")).longValue()).isNotNegative());
    }

    // ---------- Section 4/17: dashboard date selector ----------

    @Test
    @DisplayName("s4: dashboard window scopes orders and recorded value to the selected day")
    void dashboardWindowScopesFigures() {
        Seller s = sellerWithStore("Dash");
        User b = buyer();
        users.save(b);
        place(b, s.kitchen(), s.product(), "d1", 2);

        Map<String, Object> today = adminService.dashboard("today");
        assertThat(today.get("selectedPeriod")).isEqualTo("Today");
        assertThat(((Number) today.get("ordersInPeriod")).longValue()).isGreaterThanOrEqualTo(1L);

        // "Last 5 Days" spans today and the four days before it.
        Map<String, Object> last5 = adminService.dashboard("last5");
        assertThat(last5.get("selectedPeriod")).isEqualTo("Last 5 Days");
        assertThat(((Number) last5.get("ordersInPeriod")).longValue())
                .isGreaterThanOrEqualTo(((Number) today.get("ordersInPeriod")).longValue());

        // A custom day BEFORE the order must not count it; a custom day is a
        // single day, not "everything since".
        Map<String, Object> earlier = adminService.dashboard(LocalDate.now().minusDays(3).toString());
        assertThat(((Number) earlier.get("ordersInPeriod")).longValue()).isZero();

        // An unparseable value degrades to Today rather than blanking the screen.
        assertThat(adminService.dashboard("not-a-date").get("selectedPeriod")).isEqualTo("Today");
    }

    // ---------- Sections 6 / 18: the analytics the seller table depends on ----------

    @Test
    @DisplayName("s18: a captured storefront view reaches the seller table as a real number")
    void sellerAnalyticsUsesActuallyCapturedViews() {
        Seller s = sellerWithStore("Conv");
        User b = buyer();
        users.save(b);

        // Exactly what the buyer app now posts when a storefront/offering is opened.
        analyticsService.record(AnalyticsService.EV_HOMEMADE_STOREFRONT_VIEW,
                null, null, s.kitchen().getId(), null);
        analyticsService.record(AnalyticsService.EV_PRODUCT_VIEW,
                null, null, s.kitchen().getId(), null);
        place(b, s.kitchen(), s.product(), "conv", 1);

        Map<String, Object> row = rowForSeller(s);

        assertThat(((Number) row.get("storefrontViews")).longValue())
                .as("captured storefront views must reach the seller table").isEqualTo(1L);
        assertThat(((Number) row.get("offeringViews")).longValue()).isEqualTo(1L);
        assertThat(row.get("conversionRate"))
                .as("with views captured, conversion becomes measurable").isNotNull();
    }

    @Test
    @DisplayName("s6: a storefront view is counted once, not once per order")
    void viewsAreNotMultipliedByOrderCount() {
        Seller s = sellerWithStore("Multi");
        User b = buyer();
        users.save(b);
        analyticsService.record(AnalyticsService.EV_HOMEMADE_STOREFRONT_VIEW,
                null, null, s.kitchen().getId(), null);

        place(b, s.kitchen(), s.product(), "v1", 1);
        place(b, s.kitchen(), s.product(), "v2", 1);
        place(b, s.kitchen(), s.product(), "v3", 1);

        Map<String, Object> row = rowForSeller(s);

        assertThat(((Number) row.get("orders")).longValue()).isEqualTo(3L);
        assertThat(((Number) row.get("storefrontViews")).longValue())
                .as("one storefront view stays one view however many orders follow")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("s6: conversion is omitted rather than faked when no views exist")
    void conversionIsOmittedRatherThanFaked() {
        Seller s = sellerWithStore("NoViews");
        User b = buyer();
        users.save(b);
        place(b, s.kitchen(), s.product(), "c1", 1);

        assertThat(rowForSeller(s).get("conversionRate"))
                .as("no captured views means no conversion figure, never a guess")
                .isNull();
    }

    @Test
    @DisplayName("s18: payment status is captured once per real transition")
    void paymentStatusIsRecordedOncePerTransition() {
        Seller s = sellerWithStore("PayEv");
        User b = buyer();
        users.save(b);
        Order placed = place(b, s.kitchen(), s.product(), "p1", 1, PaymentStatus.PENDING);
        long before = countEvents(AnalyticsService.EV_PAYMENT_STATUS);

        orderService.markOrderAsPaid(placed.getId(), s.user());
        assertThat(countEvents(AnalyticsService.EV_PAYMENT_STATUS)).isEqualTo(before + 1);

        // Repeating it is a no-op and must not add a second event.
        orderService.markOrderAsPaid(placed.getId(), s.user());
        assertThat(countEvents(AnalyticsService.EV_PAYMENT_STATUS)).isEqualTo(before + 1);
    }

    private Map<String, Object> rowForSeller(Seller s) {
        return adminService.sellerAnalytics(new AdminService.OrderFilter()).stream()
                .filter(r -> s.user().getId().equals(r.get("sellerId")))
                .findFirst().orElseThrow(() -> new AssertionError("no analytics row for this seller"));
    }

    private long countEvents(String type) {
        return analyticsEventRepo.findAll().stream()
                .filter(e -> type.equals(e.getEventType()))
                .count();
    }
}