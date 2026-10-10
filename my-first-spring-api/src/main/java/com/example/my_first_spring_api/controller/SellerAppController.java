package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.*;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.SellerAppService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seller-app")
public class SellerAppController {

    private final SellerAppService sellerAppService;
    private final BuyerService authService;

    @Autowired
    public SellerAppController(SellerAppService sellerAppService, BuyerService authService) {
        this.sellerAppService = sellerAppService;
        this.authService = authService;
    }

    private User requireSeller(HttpSession session) {
        User user = authService.getCurrentBuyer(session);
        if (user == null) throw new BuyerNotAuthenticatedException("Authentication required. Please log in.");
        if (user.getRole() != UserRole.SELLER) throw new SellerNotAuthorizedException("Only sellers can perform this action");
        if (!user.isApprovedSeller()) {
            throw new SellerNotAuthorizedException("Your seller account is awaiting Admin approval.");
        }
        return user;
    }

    /** Parses a date parameter accepting human aliases; falls back to today when absent. */
    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return LocalDate.now();
        String d = date.trim().toLowerCase();
        if ("today".equals(d)) return LocalDate.now();
        if ("tomorrow".equals(d)) return LocalDate.now().plusDays(1);
        if ("yesterday".equals(d)) return LocalDate.now().minusDays(1);
        return LocalDate.parse(d);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SellerDashboardDto> getDashboard(HttpSession session) {
        return ResponseEntity.ok(sellerAppService.getDashboard(requireSeller(session)));
    }

    @PatchMapping("/products/{productId}/inventory")
    public ResponseEntity<ProductDto> updateInventory(@PathVariable Long productId,
                                                       @RequestBody Map<String, Integer> body,
                                                       HttpSession session) {
        Integer delta = body.getOrDefault("delta", 0);
        return ResponseEntity.ok(sellerAppService.updateInventory(productId, delta, requireSeller(session)));
    }

    @PostMapping("/products/{productId}/sold-out")
    public ResponseEntity<ProductDto> markSoldOut(@PathVariable Long productId, HttpSession session) {
        return ResponseEntity.ok(sellerAppService.markSoldOut(productId, requireSeller(session)));
    }

    @PostMapping("/products/{productId}/pause")
    public ResponseEntity<ProductDto> pauseOrders(@PathVariable Long productId, HttpSession session) {
        return ResponseEntity.ok(sellerAppService.pauseOrders(productId, requireSeller(session)));
    }

    @PostMapping("/products/{productId}/resume")
    public ResponseEntity<ProductDto> resumeOrders(@PathVariable Long productId, HttpSession session) {
        return ResponseEntity.ok(sellerAppService.resumeOrders(productId, requireSeller(session)));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<SellerTemplateDto>> getTemplates(HttpSession session) {
        return ResponseEntity.ok(sellerAppService.getTemplates(requireSeller(session)));
    }

    @PostMapping("/templates")
    public ResponseEntity<SellerTemplateDto> addTemplate(@Valid @RequestBody SellerTemplateDto dto, HttpSession session) {
        return ResponseEntity.ok(sellerAppService.addTemplate(requireSeller(session), dto));
    }

    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<Map<String, String>> deleteTemplate(@PathVariable Long templateId, HttpSession session) {
        sellerAppService.deleteTemplate(templateId, requireSeller(session));
        return ResponseEntity.ok(Map.of("message", "Template deleted"));
    }

    @PostMapping("/templates/{templateId}/publish")
    public ResponseEntity<ProductDto> publishFromTemplate(@PathVariable Long templateId,
                                                           @RequestBody Map<String, String> body,
                                                           HttpSession session) {
        LocalDate date = parseDate(body.get("availableDate"));
        return ResponseEntity.ok(sellerAppService.createProductFromTemplate(templateId, date, requireSeller(session)));
    }

    @PostMapping("/quick-posts")
    public ResponseEntity<QuickPostDto> createQuickPost(@RequestBody Map<String, Object> body,
                                                       HttpSession session) {
        Map<String, Object> values = body == null ? Map.of() : body;
        String message = values.get("message") == null ? null : String.valueOf(values.get("message"));
        String imageData = values.get("imageData") == null ? null : String.valueOf(values.get("imageData"));
        String requestId = values.get("requestId") == null ? null : String.valueOf(values.get("requestId"));
        LocalDate postedDate = values.get("postedDate") == null ? null
                : LocalDate.parse(String.valueOf(values.get("postedDate")));
        return ResponseEntity.ok(sellerAppService.createQuickPost(
                requireSeller(session), message, imageData, requestId, postedDate));
    }

    @GetMapping("/quick-posts")
    public ResponseEntity<List<QuickPostDto>> getQuickPosts(HttpSession session) {
        return ResponseEntity.ok(sellerAppService.getQuickPosts(requireSeller(session)));
    }

    @PostMapping("/parse-message")
    public ResponseEntity<QuickPostParseResultDto> parseMessage(@RequestBody(required = false) Map<String, String> body, HttpSession session) {
        requireSeller(session);
        String message = body != null ? body.getOrDefault("message", "") : "";
        return ResponseEntity.ok(sellerAppService.parseQuickPost(message));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ProductDto>> getHistory(HttpSession session) {
        return ResponseEntity.ok(sellerAppService.getRecentOfferings(requireSeller(session)));
    }

    @PostMapping("/batch-republish")
    public ResponseEntity<List<ProductDto>> batchRepublish(@RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        Object rawIds = body == null ? null : body.get("productIds");
        List<Long> productIds;
        if (rawIds == null) {
            productIds = List.of();
        } else if (rawIds instanceof List<?> rawList) {
            productIds = rawList.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(n -> n instanceof Number)
                    .map(n -> ((Number) n).longValue())
                    .toList();
        } else {
            throw new IllegalArgumentException("productIds must be a list of numbers.");
        }
        LocalDate date = parseDate((String) (body != null ? body.get("availableDate") : null));
        return ResponseEntity.ok(sellerAppService.batchRepublish(productIds, date, requireSeller(session)));
    }

    @GetMapping("/orders/summary")
    public ResponseEntity<SellerOrderSummaryDto> getOrderSummary(@RequestParam(required = false) String date,
                                                                   HttpSession session) {
        LocalDate d = date != null && "all".equalsIgnoreCase(date.trim()) ? null : parseDate(date);
        return ResponseEntity.ok(sellerAppService.getOrderSummary(requireSeller(session), d));
    }

    /**
     * Offering drill-down payload.
     *
     * <p>{@code delivery} is the third, independent filter
     * ({@code delivered} / {@code not_delivered}) that combines with the society
     * and payment filters. It only changes the rendered subset - it never
     * writes.</p>
     */
    @GetMapping("/orders/product/{productId}")
    public ResponseEntity<OrderItemDetailDto> getOrderItemDetail(@PathVariable Long productId,
                                                                   @RequestParam(required = false) String date,
                                                                   @RequestParam(required = false) String society,
                                                                   @RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) String delivery,
                                                                   HttpSession session) {
        LocalDate d = parseDate(date);
        return ResponseEntity.ok(sellerAppService.getOrderItemDetail(
                requireSeller(session), productId, d, society, status, delivery));
    }

    /**
     * Records ONE order's Delivered checkbox.
     *
     * <p>The seller is taken from the session, so the order id in the path can
     * only ever address an order of the caller's own kitchen.</p>
     */
    @PatchMapping("/orders/{orderId}/delivery-status")
    public ResponseEntity<OrderDto> updateDeliveryStatus(@PathVariable Long orderId,
                                                          @Valid @RequestBody UpdateDeliveryStatusRequest request,
                                                          HttpSession session) {
        return ResponseEntity.ok(sellerAppService.updateDeliveryStatus(
                requireSeller(session), orderId, request.getDeliveryStatus()));
    }

    /**
     * Bulk "Mark All Delivered" for one offering on one date.
     *
     * <p>The returned counts come from the backend's own scope calculation, so
     * the client can display the true result even when its view was filtered or
     * stale.</p>
     */
    @PostMapping("/orders/product/{productId}/mark-all-delivered")
    public ResponseEntity<DeliveryProgressDto> markAllOfferingOrdersDelivered(@PathVariable Long productId,
                                                                              @RequestParam(required = false) String date,
                                                                              HttpSession session) {
        LocalDate d = parseDate(date);
        return ResponseEntity.ok(sellerAppService.markAllOfferingOrdersDelivered(
                requireSeller(session), productId, d));
    }

    @GetMapping("/earnings")
    public ResponseEntity<SellerEarningsDto> getEarnings(HttpSession session) {
        return ResponseEntity.ok(sellerAppService.getEarnings(requireSeller(session)));
    }
}
