package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.*;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.UserRepository;
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
    private final UserRepository userRepository;

    @Autowired
    public SellerAppController(SellerAppService sellerAppService, BuyerService authService, UserRepository userRepository) {
        this.sellerAppService = sellerAppService;
        this.authService = authService;
        this.userRepository = userRepository;
    }

    private User requireSeller(HttpSession session) {
        User user = authService.getCurrentBuyer(session);
        if (user == null) throw new BuyerNotAuthenticatedException("Authentication required. Please log in.");
        if (user.getRole() != UserRole.SELLER) throw new SellerNotAuthorizedException("Only sellers can perform this action");
        return user;
    }

    /**
     * Demo auto-login: authenticates as the demo seller (Aarti).
     * Used for client demo so the app opens directly to the dashboard.
     */
    @PostMapping("/demo-login")
    public ResponseEntity<AuthResponseDto> demoLogin(HttpSession session) {
        User aarti = userRepository.findByMobileNumber("9100000001")
                .orElseGet(() -> {
                    User u = new User("Aarti", "9100000001", "A-101", UserRole.SELLER);
                    u.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
                    u.setSociety("Sunshine Society");
                    u.setBuilding("Building B");
                    return userRepository.save(u);
                });
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, aarti.getId());
        return ResponseEntity.ok(new AuthResponseDto(
                true, "Demo login successful",
                aarti.getId(), aarti.getName(), aarti.getMobileNumber(),
                aarti.getFlatHouseNumber(), aarti.getRole().name(), aarti.getSellerApprovalStatus()));
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
        LocalDate d = parseDate(date);
        return ResponseEntity.ok(sellerAppService.getOrderSummary(requireSeller(session), d));
    }

    @GetMapping("/orders/product/{productId}")
    public ResponseEntity<OrderItemDetailDto> getOrderItemDetail(@PathVariable Long productId,
                                                                  @RequestParam(required = false) String date,
                                                                  HttpSession session) {
        LocalDate d = parseDate(date);
        return ResponseEntity.ok(sellerAppService.getOrderItemDetail(requireSeller(session), productId, d));
    }

    @GetMapping("/earnings")
    public ResponseEntity<SellerEarningsDto> getEarnings(HttpSession session) {
        return ResponseEntity.ok(sellerAppService.getEarnings(requireSeller(session)));
    }
}
