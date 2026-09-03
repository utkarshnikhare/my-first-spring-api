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
        if (user == null) throw new BuyerNotAuthenticatedException("Authentication required. Please verify your mobile number via OTP.");
        if (user.getRole() != UserRole.SELLER) throw new SellerNotAuthorizedException("Only sellers can perform this action");
        return user;
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
        LocalDate date = body.get("availableDate") != null ? LocalDate.parse(body.get("availableDate")) : LocalDate.now();
        return ResponseEntity.ok(sellerAppService.createProductFromTemplate(templateId, date, requireSeller(session)));
    }

    @PostMapping("/parse-message")
    public ResponseEntity<QuickPostParseResultDto> parseMessage(@RequestBody Map<String, String> body, HttpSession session) {
        requireSeller(session);
        String message = body.getOrDefault("message", "");
        return ResponseEntity.ok(sellerAppService.parseQuickPost(message));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ProductDto>> getHistory(HttpSession session) {
        return ResponseEntity.ok(sellerAppService.getRecentOfferings(requireSeller(session)));
    }

    @PostMapping("/batch-republish")
    public ResponseEntity<List<ProductDto>> batchRepublish(@RequestBody Map<String, Object> body, HttpSession session) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.getOrDefault("productIds", List.of());
        List<Long> productIds = ids.stream().filter(java.util.Objects::nonNull).map(n -> n.longValue()).toList();
        String dateStr = (String) body.get("availableDate");
        LocalDate date = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();
        return ResponseEntity.ok(sellerAppService.batchRepublish(productIds, date, requireSeller(session)));
    }

    @GetMapping("/orders/summary")
    public ResponseEntity<SellerOrderSummaryDto> getOrderSummary(@RequestParam(required = false) String date,
                                                                  HttpSession session) {
        LocalDate d = date != null ? LocalDate.parse(date) : LocalDate.now();
        return ResponseEntity.ok(sellerAppService.getOrderSummary(requireSeller(session), d));
    }

    @GetMapping("/orders/product/{productId}")
    public ResponseEntity<OrderItemDetailDto> getOrderItemDetail(@PathVariable Long productId,
                                                                  @RequestParam(required = false) String date,
                                                                  HttpSession session) {
        LocalDate d = date != null ? LocalDate.parse(date) : LocalDate.now();
        return ResponseEntity.ok(sellerAppService.getOrderItemDetail(requireSeller(session), productId, d));
    }

    @GetMapping("/earnings")
    public ResponseEntity<SellerEarningsDto> getEarnings(HttpSession session) {
        return ResponseEntity.ok(sellerAppService.getEarnings(requireSeller(session)));
    }
}