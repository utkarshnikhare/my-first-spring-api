package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.CoverageOptionDto;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.AdminService;
import com.example.my_first_spring_api.service.BuyerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final BuyerService buyerService;

    @Autowired
    public AdminController(AdminService adminService, BuyerService buyerService) {
        this.adminService = adminService;
        this.buyerService = buyerService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(adminService.dashboard());
    }

    @GetMapping("/buyers")
    public ResponseEntity<List<Map<String, Object>>> buyers() {
        return ResponseEntity.ok(adminService.buyers());
    }

    /**
     * "Why can't this buyer see this kitchen?" - derived from the existing
     * KitchenVisibility predicates. Read-only; it never mutates buyer or kitchen data
     * and never changes normal discovery.
     */
    @GetMapping("/diagnostics/visibility")
    public ResponseEntity<Map<String, Object>> visibilityDiagnostic(
            @RequestParam Long buyerId,
            @RequestParam(required = false) Long kitchenId) {
        return ResponseEntity.ok(adminService.visibilityDiagnostic(buyerId, kitchenId));
    }

    /** Lightweight factual runtime status. No monitoring platform, no invented metrics. */
    @GetMapping("/system-health")
    public ResponseEntity<Map<String, Object>> systemHealth() {
        return ResponseEntity.ok(adminService.systemHealth());
    }

    @GetMapping("/sellers")
    public ResponseEntity<List<Map<String, Object>>> sellers(
            @RequestParam(value = "status", required = false) SellerApprovalStatus status) {
        return ResponseEntity.ok(adminService.sellers(status));
    }

    @GetMapping("/kitchens")
    public ResponseEntity<List<Map<String, Object>>> kitchens(@RequestParam(required = false) String sellerType) {
        return ResponseEntity.ok(adminService.kitchens(sellerType));
    }

    @PatchMapping("/kitchens/{kitchenId}/service-areas")
    public ResponseEntity<Map<String, Object>> updateKitchenServiceAreas(@PathVariable Long kitchenId,
                                                                        @RequestBody Map<String, Object> body) {
        // The ID payload is authoritative when present. The legacy "serviceAreas"
        // string is still accepted for older callers and is funnelled through the
        // same service, so coverage is never persisted by two different mechanisms.
        String serviceAreas = body != null && body.get("serviceAreas") != null
                ? String.valueOf(body.get("serviceAreas")) : null;
        Long areaId = body != null && body.get("areaId") != null
                ? Long.valueOf(String.valueOf(body.get("areaId"))) : null;
        List<Long> societyIds = null;
        if (body != null && body.get("societyIds") != null) {
            Object raw = body.get("societyIds");
            societyIds = new java.util.ArrayList<>();
            if (raw instanceof Iterable<?> iter) {
                for (Object o : iter) {
                    if (o != null) societyIds.add(Long.valueOf(String.valueOf(o)));
                }
            }
        }
        return ResponseEntity.ok(adminService.updateKitchenServiceAreas(kitchenId, serviceAreas, areaId, societyIds));
    }

    /** Existing societies assignable as service areas — same source sellers use. */
    @GetMapping("/societies")
    public ResponseEntity<List<String>> societies() {
        return ResponseEntity.ok(adminService.societies());
    }

    /**
     * Active Areas, each with its active societies, carrying the IDs the coverage
     * editor submits. Read from the same {@code LocationService} the seller picker
     * uses, so there is exactly one source of coverage choices.
     */
    @GetMapping("/coverage-options")
    public ResponseEntity<List<CoverageOptionDto>> coverageOptions() {
        return ResponseEntity.ok(adminService.coverageOptions());
    }

    // ==================== Manage Areas & Societies ====================

    /**
     * The full Area -&gt; Society master, including inactive records so the Admin
     * screen can re-enable them. The buyer and seller dropdowns read only the
     * active subset through their own endpoints.
     */
    @GetMapping("/locations")
    public ResponseEntity<Map<String, Object>> locations() {
        return ResponseEntity.ok(adminService.locations());
    }

    @PostMapping("/areas")
    public ResponseEntity<Map<String, Object>> createArea(
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(adminService.createArea(bodyStr(body, "name")));
    }

    /** Rename and/or enable-disable an Area. Absent fields are left untouched. */
    @PatchMapping("/areas/{areaId}")
    public ResponseEntity<Map<String, Object>> updateArea(@PathVariable Long areaId,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateArea(areaId, bodyStr(body, "name"), bodyBool(body, "active")));
    }

    @PostMapping("/societies")
    public ResponseEntity<Map<String, Object>> createSociety(
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(adminService.createSociety(bodyLong(body, "areaId"), bodyStr(body, "name")));
    }

    /** Rename and/or enable-disable a Society. Absent fields are left untouched. */
    @PatchMapping("/societies/{societyId}")
    public ResponseEntity<Map<String, Object>> updateSociety(@PathVariable Long societyId,
                                                             @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateSociety(societyId, bodyStr(body, "name"),
                bodyBool(body, "active")));
    }

    private static String bodyStr(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long bodyLong(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid id supplied for " + key + ".");
        }
    }

    private static Boolean bodyBool(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @GetMapping("/offerings")
    public ResponseEntity<List<Map<String, Object>>> offerings() {
        return ResponseEntity.ok(adminService.offerings());
    }

    /**
     * Admin V1 Orders monitoring list.
     *
     * <p>All filters are optional and compose with AND. The delivery axis reads
     * the SAME shared Order delivery state the Seller tracker writes, so this
     * endpoint is read-only monitoring and cannot become a second delivery
     * system.</p>
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> orders(
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "areaId", required = false) Long areaId,
            @RequestParam(value = "societyId", required = false) Long societyId,
            @RequestParam(value = "sellerId", required = false) Long sellerId,
            @RequestParam(value = "buyerId", required = false) Long buyerId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "payment", required = false) String payment,
            @RequestParam(value = "delivery", required = false) String delivery,
            @RequestParam(value = "status", required = false) String status) {
        AdminService.OrderFilter f = new AdminService.OrderFilter();
        f.legacyFilter = filter;
        f.search = search;
        f.date = date;
        f.areaId = areaId;
        f.societyId = societyId;
        f.sellerId = sellerId;
        f.buyerId = buyerId;
        f.category = category;
        f.payment = payment;
        f.delivery = delivery;
        f.status = status;
        return ResponseEntity.ok(adminService.orders(f));
    }

    /**
     * Admin V1 CSV export.
     *
     * <p>Honours the SAME filter parameters as {@link #orders} so a downloaded
     * file can never contain more rows than the operator is looking at. Served as
     * an attachment; the Admin-only rule on {@code /api/admin/**} still applies,
     * so no authorization is bypassed by downloading.</p>
     */
    @GetMapping("/exports/{domain}.csv")
    public ResponseEntity<String> export(
            @PathVariable String domain,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "areaId", required = false) Long areaId,
            @RequestParam(value = "societyId", required = false) Long societyId,
            @RequestParam(value = "sellerId", required = false) Long sellerId,
            @RequestParam(value = "buyerId", required = false) Long buyerId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "payment", required = false) String payment,
            @RequestParam(value = "delivery", required = false) String delivery,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search) {
        AdminService.OrderFilter f = new AdminService.OrderFilter();
        f.date = date;
        f.areaId = areaId;
        f.societyId = societyId;
        f.sellerId = sellerId;
        f.buyerId = buyerId;
        f.category = category;
        f.payment = payment;
        f.delivery = delivery;
        f.status = status;
        f.search = search;
        String csv = adminService.exportCsv(domain, f);
        String safe = domain.replaceAll("[^A-Za-z]", "").toLowerCase();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sociomart-admin-" + safe + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    /**
     * The single Admin V1 "Needs Attention" panel.
     *
     * <p>One compact endpoint so the dashboard does not have to re-derive the
     * same counts from four different payloads.</p>
     */
    @GetMapping("/attention")
    public ResponseEntity<List<Map<String, Object>>> attention() {
        return ResponseEntity.ok(adminService.attentionItems());
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> orderDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.orderDetail(id));
    }

    @GetMapping("/enquiries")
    public ResponseEntity<List<Map<String, Object>>> enquiries() {
        return ResponseEntity.ok(adminService.enquiries());
    }

    @GetMapping("/sellers/pending")
    public ResponseEntity<List<Map<String, Object>>> pendingSellers() {
        return ResponseEntity.ok(adminService.pendingSellers().stream()
                .map(AdminController::toSellerSummary).collect(Collectors.toList()));
    }

    @PostMapping("/sellers/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveSeller(@PathVariable Long id, HttpSession session) {
        User admin = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(toSellerSummary(adminService.approveSeller(id, admin)));
    }

    @PostMapping("/sellers/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectSeller(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, String> body,
                                                            HttpSession session) {
        User admin = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(toSellerSummary(adminService.rejectSeller(id,
                body != null ? body.get("reason") : null, admin)));
    }

    @PostMapping("/sellers/{id}/suspend")
    public ResponseEntity<Map<String, Object>> suspendSeller(@PathVariable Long id,
                                                             @RequestBody(required = false) Map<String, String> body,
                                                             HttpSession session) {
        User admin = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(toSellerSummary(adminService.suspendSeller(id,
                body != null ? body.get("reason") : null, admin)));
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ResponseEntity.ok(adminService.analyticsSummary());
    }

    @GetMapping("/traffic")
    public ResponseEntity<Map<String, Object>> traffic(
            @RequestParam(value = "period", required = false, defaultValue = "today") String period) {
        return ResponseEntity.ok(adminService.traffic(period));
    }

    static Map<String, Object> toSellerSummary(User seller) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", seller.getId());
        m.put("name", seller.getName());
        m.put("mobileNumber", seller.getMobileNumber());
        m.put("sellerApprovalStatus", seller.getSellerApprovalStatus());
        m.put("statusReason", seller.getSellerStatusReason());
        m.put("approvedAt", seller.getApprovedAt());
        m.put("registeredAt", seller.getCreatedAt());
        return m;
    }
}
