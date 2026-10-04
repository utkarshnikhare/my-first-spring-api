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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers Admin handover requirements that had no implementation before this
 * work: audit log (s14), Request Changes and storefront controls (s7.1, s7.3),
 * buyer block/unblock/support note (s8), configurable retention and daily
 * aggregates (s12), seller analytics and recorded order value (s6, s10), and
 * export hardening (s12.1).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-v2;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class AdminV2HandoverTest {

    @Autowired private AdminService adminService;
    @Autowired private RetentionService retentionService;
    @Autowired private AdminAuditLogRepository auditRepo;
    @Autowired private OrderDailyAggregateRepository aggregateRepo;
    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private OrderRepository orders;
    @Autowired private ProductRepository products;

    private static int seq = 0;
    private final String sfx = "v2" + (seq++);

    /** The acting Admin. The test profile does not seed one, so create it once. */
    private User admin() {
        return users.findByMobileNumber("9000000001").orElseGet(() -> {
            User a = new User("Admin " + sfx, "9000000001", "A-1", UserRole.SUPER_ADMIN);
            return users.save(a);
        });
    }

    private User seller(String name) {
        User s = new User(name + sfx, "92" + Math.abs((name + sfx).hashCode() % 100000), "K-1", UserRole.SELLER);
        s.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        return users.save(s);
    }

    private User buyer() {
        return users.save(new User("Buyer " + sfx, "93" + Math.abs(sfx.hashCode() % 100000), "B-1", UserRole.BUYER));
    }

    record Seller(User user, Kitchen kitchen, Product product) {}

    /** An approved seller with one kitchen and one offering. */
    private Seller sellerWithStore(String name) {
        User s = seller(name);
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
        Order o = new Order(b, k);
        o.setOrderNumber("ORD-" + sfx + suffix);
        o.setOrderStatus(OrderStatus.CONFIRMED);
        o.setPaymentStatus(PaymentStatus.PAID);
        o.addItem(new OrderItem(p, qty, BigDecimal.valueOf(40)));
        o.recalculateTotal();
        return orders.save(o);
    }

    // ---------- Section 14: audit log ----------

    @Test
    @DisplayName("s14: approval decisions record actor, old/new state, reason and timestamp")
    void approvalWritesAuditTrail() {
        User s = seller("Audit");
        User a = admin();

        adminService.approveSeller(s.getId(), a);
        adminService.requestSellerChanges(s.getId(), "please upload GST proof", a);
        adminService.suspendSeller(s.getId(), "repeated no-show", a);

        List<AdminAuditLog> trail = auditRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("SELLER", s.getId());
        List<String> actions = trail.stream().map(AdminAuditLog::getAction).toList();
        assertThat(actions).contains(AdminAuditService.SELLER_APPROVED,
                AdminAuditService.SELLER_CHANGES_REQUESTED, AdminAuditService.SELLER_SUSPENDED);

        AdminAuditLog suspend = trail.stream()
                .filter(l -> AdminAuditService.SELLER_SUSPENDED.equals(l.getAction())).findFirst().orElseThrow();
        assertThat(suspend.getActorId()).isEqualTo(a.getId());
        assertThat(suspend.getActorName()).isEqualTo(a.getName());
        assertThat(suspend.getReason()).isEqualTo("repeated no-show");
        assertThat(suspend.getOldState()).isEqualTo("CHANGES_REQUESTED");
        assertThat(suspend.getNewState()).isEqualTo("SUSPENDED");
        assertThat(suspend.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("s14: the audit log read model is empty-safe")
    void auditLogReadModel() {
        assertThat(adminService.auditLog(0)).isNotNull();
        assertThat(adminService.auditLog(10)).isNotNull();
        assertThat(adminService.auditForTarget("SELLER", 999999L)).isEmpty();
        assertThat(adminService.auditForTarget(null, null)).isEmpty();
    }

    @Test
    @DisplayName("s7.1: approval stores the acting Admin, not only a timestamp")
    void approvalStoresActingAdmin() {
        User s = seller("Acted");
        User a = admin();
        adminService.approveSeller(s.getId(), a);

        User reloaded = users.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getApprovedAt()).isNotNull();
        assertThat(reloaded.getApprovedBy()).isNotNull();
        assertThat(reloaded.getApprovedBy().getId()).isEqualTo(a.getId());
    }

    @Test
    @DisplayName("s7.1: Request Changes needs a reason and keeps the seller out of service")
    void requestChangesNeedsReason() {
        User s = seller("Changes");
        User a = admin();

        assertThatThrownBy(() -> adminService.requestSellerChanges(s.getId(), "  ", a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");

        User updated = adminService.requestSellerChanges(s.getId(), "fix your society", a);
        assertThat(updated.getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.CHANGES_REQUESTED);
        assertThat(updated.getSellerStatusReason()).isEqualTo("fix your society");
        assertThat(updated.isApprovedSeller()).as("changes-requested seller must not serve").isFalse();
    }

    @Test
    @DisplayName("s7.3: suspend requires a reason")
    void suspendRequiresReason() {
        User s = seller("SuspendMe");
        assertThatThrownBy(() -> adminService.suspendSeller(s.getId(), null, admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
    }

    // ---------- Section 7.3: storefront controls ----------

    @Test
    @DisplayName("s7.3: pause/resume/remove drive and audit the storefront state")
    void storefrontLifecycle() {
        Seller s = sellerWithStore("Life");
        User a = admin();

        Map<String, Object> paused = adminService.pauseStorefront(s.kitchen().getId(), "stock issue", a);
        assertThat(paused.get("paused")).isEqualTo(Boolean.TRUE);
        assertThat(kitchens.findById(s.kitchen().getId()).orElseThrow().isStorefrontPaused()).isTrue();

        Map<String, Object> resumed = adminService.resumeStorefront(s.kitchen().getId(), a);
        assertThat(resumed.get("paused")).isEqualTo(Boolean.FALSE);
        assertThat(kitchens.findById(s.kitchen().getId()).orElseThrow().isStorefrontPaused()).isFalse();

        assertThatThrownBy(() -> adminService.removeStorefront(s.kitchen().getId(), "", a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");

        Map<String, Object> removed = adminService.removeStorefront(s.kitchen().getId(), "duplicate store", a);
        assertThat(removed.get("removed")).isEqualTo(Boolean.TRUE);
        assertThat(kitchens.findById(s.kitchen().getId())).as("soft removal keeps the row").isPresent();
        assertThat(users.findById(s.user().getId()).orElseThrow().isStorefrontRemoved()).isTrue();

        List<String> actions = auditRepo
                .findByTargetTypeAndTargetIdOrderByCreatedAtDesc("STOREFRONT", s.kitchen().getId())
                .stream().map(AdminAuditLog::getAction).toList();
        assertThat(actions).contains(AdminAuditService.STOREFRONT_PAUSED,
                AdminAuditService.STOREFRONT_RESUMED, AdminAuditService.STOREFRONT_REMOVED);
    }

    @Test
    @DisplayName("s7.3: an unknown storefront is a clear error, not a silent success")
    void pauseUnknownStorefront() {
        assertThatThrownBy(() -> adminService.pauseStorefront(999999L, null, admin()))
                .isInstanceOf(RuntimeException.class);
    }

    // ---------- Section 8: buyer support ----------

    @Test
    @DisplayName("s8: block needs a reason, unblock restores, both are audited")
    void buyerBlockUnblock() {
        User b = buyer();
        User a = admin();

        assertThatThrownBy(() -> adminService.blockBuyer(b.getId(), null, a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");

        assertThat(adminService.blockBuyer(b.getId(), "seller-reported misuse", a).get("accountStatus"))
                .isEqualTo("BLOCKED");
        assertThat(users.findById(b.getId()).orElseThrow().isBlocked()).isTrue();

        assertThat(adminService.unblockBuyer(b.getId(), "resolved", a).get("accountStatus"))
                .isEqualTo("ACTIVE");
        assertThat(users.findById(b.getId()).orElseThrow().isBlocked()).isFalse();

        List<String> actions = auditRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("BUYER", b.getId())
                .stream().map(AdminAuditLog::getAction).toList();
        assertThat(actions).contains(AdminAuditService.BUYER_BLOCKED, AdminAuditService.BUYER_UNBLOCKED);
    }

    @Test
    @DisplayName("s8: buyer detail exposes status; the support note stays in the admin payload")
    void buyerDetail() {
        User b = buyer();
        adminService.blockBuyer(b.getId(), "misuse", admin());
        adminService.saveBuyerSupportNote(b.getId(), "called twice, resolved", admin());

        Map<String, Object> detail = adminService.buyerDetail(b.getId());
        assertThat(detail.get("accountStatus")).isEqualTo("BLOCKED");
        assertThat(detail.get("supportNote")).isEqualTo("called twice, resolved");
        assertThat(detail.get("orderCount")).isEqualTo(0);
        assertThat(detail).containsKey("auditHistory");
    }

    @Test
    @DisplayName("s8: admin controls refuse an unknown or wrong-role target")
    void controlsRejectWrongTarget() {
        assertThatThrownBy(() -> adminService.blockBuyer(999999L, "x", admin()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adminService.blockBuyer(admin().getId(), "x", admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a buyer");
    }

    // ---------- Section 12: retention ----------

    @Test
    @DisplayName("s12: retention_days is configurable at runtime, not hard-coded")
    void retentionIsConfigurable() {
        Object original = adminService.retention().get("retentionDays");

        assertThat(adminService.setRetentionDays(7, admin()).get("retentionDays")).isEqualTo(7);
        assertThat(adminService.retention().get("retentionDays")).isEqualTo(7);
        assertThat(adminService.setRetentionDays(30, admin()).get("retentionDays")).isEqualTo(30);
        assertThat(adminService.setRetentionDays(0, admin()).get("retentionDays")).isEqualTo(1);
        assertThat(adminService.setRetentionDays(999999, admin()).get("retentionDays")).isEqualTo(3650);

        if (original instanceof Integer i && i > 0) adminService.setRetentionDays(i, admin());
    }

    @Test
    @DisplayName("s12: no destructive purge runs and the preview is reported honestly")
    void retentionIsNonDestructive() {
        Map<String, Object> status = adminService.retention();
        assertThat(status.get("destructivePurgeEnabled")).isEqualTo(Boolean.FALSE);
        assertThat(status).containsKeys("retentionDays", "purgeCutoffDate", "detailedRowsPastWindow", "settingKey");
    }

    @Test
    @DisplayName("s12: an order fact is rolled into the durable daily aggregate")
    void orderFactIsAggregated() {
        Seller s = sellerWithStore("Agg");
        Order placed = place(buyer(), s.kitchen(), s.product(), "a1", 2);

        retentionService.recordOrderFact(placed);

        LocalDate today = LocalDate.now();
        assertThat(aggregateRepo.sumOrderCount(today.minusDays(1), today.plusDays(1)))
                .as("aggregate is independent of the order row").isGreaterThanOrEqualTo(1);
        assertThat(aggregateRepo.sumValue(today.minusDays(1), today.plusDays(1)))
                .isGreaterThanOrEqualTo(BigDecimal.valueOf(80));
    }

    // ---------- Sections 6 and 10: analytics / commercial ----------

    @Test
    @DisplayName("s6/s10: seller analytics and recorded order value agree and never say revenue")
    void sellerAnalyticsAndRecordedValue() {
        Seller s = sellerWithStore("An");
        User b = buyer();
        place(b, s.kitchen(), s.product(), "a2", 1);
        place(b, s.kitchen(), s.product(), "a3", 1);

        AdminService.OrderFilter f = new AdminService.OrderFilter();
        f.sellerId = s.user().getId();

        List<Map<String, Object>> rows = adminService.sellerAnalytics(f);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("sellerId")).isEqualTo(s.user().getId());
        assertThat((Long) row.get("orders")).isEqualTo(2L);
        assertThat((BigDecimal) row.get("recordedOrderValue")).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat((BigDecimal) row.get("averageOrderValue")).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(row).containsKeys("storefrontViews", "offeringViews", "conversionRate");

        Map<String, Object> summary = adminService.recordedOrderValueSummary(f);
        assertThat(summary.get("orderCount")).isEqualTo(2L);
        assertThat((BigDecimal) summary.get("recordedOrderValue")).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat((BigDecimal) summary.get("averageOrderValue")).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(summary).doesNotContainKey("revenue");
        assertThat(summary).doesNotContainKey("platformRevenue");
    }

    @Test
    @DisplayName("s6: conversion is null, never fabricated, when no traffic event was recorded")
    void conversionIsNotFabricated() {
        Seller s = sellerWithStore("NoTraf");
        place(buyer(), s.kitchen(), s.product(), "a4", 1);

        AdminService.OrderFilter f = new AdminService.OrderFilter();
        f.sellerId = s.user().getId();
        Map<String, Object> row = adminService.sellerAnalytics(f).get(0);
        assertThat(row.get("storefrontViews")).isEqualTo(0L);
        assertThat(row.get("conversionRate")).as("no views means no conversion claim").isNull();
    }

    @Test
    @DisplayName("s7.2: seller detail reports enabled types, storefronts, value and audit history")
    void sellerDetail() {
        Seller s = sellerWithStore("Det");
        adminService.suspendSeller(s.user().getId(), "under review", admin());

        Map<String, Object> d = adminService.sellerDetail(s.user().getId());
        assertThat(d.get("enabledTypes")).isEqualTo("KITCHEN");
        assertThat(d.get("status")).isEqualTo(SellerApprovalStatus.SUSPENDED);
        assertThat(d.get("statusReason")).isEqualTo("under review");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> storefronts = (List<Map<String, Object>>) d.get("storefronts");
        assertThat(storefronts).hasSize(1);
        assertThat(storefronts.get(0)).containsKeys("id", "name", "sellerType", "paused",
                "storefrontViews", "offeringViews");
        assertThat(d).containsKeys("recordedOrderValue", "averageOrderValue", "auditHistory", "approvedBy");
        assertThat((List<?>) d.get("auditHistory")).isNotEmpty();
    }

    // ---------- Section 12.1: export hardening ----------

    @Test
    @DisplayName("s12.1: an unknown export domain is rejected instead of silently returning Orders")
    void unknownExportDomainIsRejected() {
        AdminService.OrderFilter f = new AdminService.OrderFilter();
        assertThatThrownBy(() -> adminService.exportCsv("kitchens", f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown export");
        assertThatThrownBy(() -> adminService.exportCsv("bogus", f))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(adminService.exportCsv("orders", f)).contains("Order ID");
        assertThat(adminService.exportCsv("sellers", f)).contains("Seller ID");
        assertThat(adminService.exportCsv("buyers", f)).contains("Buyer ID");
        assertThat(adminService.exportCsv("analytics", f)).isNotBlank();
    }

    @Test
    @DisplayName("s12.1: CSV formula injection is neutralised for user-supplied values")
    void csvFormulaInjectionIsNeutralised() {
        assertThat(AdminService.isFormulaLike("=cmd|' /C calc'!A0")).isTrue();
        assertThat(AdminService.isFormulaLike("+1+1")).isTrue();
        assertThat(AdminService.isFormulaLike("-2+3")).isTrue();
        assertThat(AdminService.isFormulaLike("@SUM(A1)")).isTrue();
        assertThat(AdminService.isFormulaLike("\t=1+1")).isTrue();
        assertThat(AdminService.isFormulaLike("Poha")).isFalse();
        assertThat(AdminService.isFormulaLike("")).isFalse();

        String csv = adminService.exportCsv("orders", new AdminService.OrderFilter());
        for (String line : csv.split("\n")) {
            if (line.startsWith("#") || line.isBlank()) continue;
            for (String cell : line.split(",")) {
                assertThat(cell.startsWith("=") || cell.startsWith("@") || cell.startsWith("+"))
                        .as("no exported cell may be a bare formula: " + cell).isFalse();
            }
        }
    }

    @Test
    @DisplayName("s12.1: exports carry a timestamp, headers and the correct money label")
    void exportCarriesTimestampAndHeaders() {
        String csv = adminService.exportCsv("orders", new AdminService.OrderFilter());
        assertThat(csv).contains("# Generated at ");
        assertThat(csv).contains("Order ID,Order Number");
        assertThat(csv).contains("Recorded Order Value");
        assertThat(csv).doesNotContain("Revenue");
    }
}
