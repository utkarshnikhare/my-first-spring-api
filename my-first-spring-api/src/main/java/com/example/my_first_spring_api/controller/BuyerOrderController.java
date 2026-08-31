package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.dto.UpdatePaymentStatusRequest;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.OrderService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/buyer/orders")
public class BuyerOrderController {

    private final OrderService orderService;
    private final BuyerService buyerService;

    @Autowired
    public BuyerOrderController(OrderService orderService, BuyerService buyerService) {
        this.orderService = orderService;
        this.buyerService = buyerService;
    }

    @PostMapping("/draft")
    public ResponseEntity<OrderDto> createOrUpdateDraft(@RequestParam("kitchenId") Long kitchenId,
                                                        @Valid @RequestBody List<OrderItemRequest> items,
                                                        HttpSession session) {
        return ResponseEntity.ok(orderService.createOrUpdateDraftOrder(kitchenId, items, session));
    }

    @GetMapping("/draft")
    public ResponseEntity<OrderDto> getCurrentDraft(HttpSession session) {
        OrderDto draft = orderService.getCurrentDraftOrder(session);
        if (draft == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(draft);
    }

    @DeleteMapping("/draft")
    public ResponseEntity<Map<String, String>> clearDraft(HttpSession session) {
        orderService.clearDraftOrder(session);
        return ResponseEntity.ok(Map.of("message", "Draft order cleared"));
    }

    @PostMapping("/place")
    public ResponseEntity<OrderDto> placeOrder(@Valid @RequestBody PlaceOrderRequest request, HttpSession session) {
        return ResponseEntity.ok(orderService.placeOrder(
                request.getPaymentStatus(), request.getBuyerDetails(),
                request.getCustomInstructions(), session));
    }

    @GetMapping("/my")
    public ResponseEntity<Map<String, List<OrderDto>>> getMyOrders(HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(orderService.getMyOrders(buyer));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrderDetails(@PathVariable Long orderId, HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(orderService.getOrderDetails(orderId, buyer));
    }

    @PostMapping("/{orderId}/rate")
    public ResponseEntity<OrderDto> rateOrder(@PathVariable Long orderId,
                                              @RequestParam("rating") Integer rating, HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(orderService.rateOrder(orderId, rating, buyer));
    }

    @PostMapping("/{orderId}/reorder")
    public ResponseEntity<OrderDto> reorder(@PathVariable Long orderId, HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(orderService.reorder(orderId, session, buyer));
    }

    @PatchMapping("/{orderId}/payment-status")
    public ResponseEntity<OrderDto> updatePaymentStatus(@PathVariable Long orderId,
                                                        @Valid @RequestBody UpdatePaymentStatusRequest request,
                                                        HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(orderService.updatePaymentStatus(orderId, request.getPaymentStatus(), buyer));
    }
}
