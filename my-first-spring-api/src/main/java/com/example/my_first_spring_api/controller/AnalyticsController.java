package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Lightweight endpoint for frontend-reported interaction events.
 * Distinguishes user intent (click) from backend completion (order placed / enquiry submitted).
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Autowired
    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/event")
    public ResponseEntity<Map<String, String>> recordEvent(@RequestBody Map<String, String> body) {
        String type = body.get("type");
        if (type == null || type.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Event type is required"));
        }
        // Whitelist allowed frontend-reported event types to prevent arbitrary event injection.
        switch (type) {
            case AnalyticsService.EV_ORDER_NOW_CLICK:
            case AnalyticsService.EV_ENQUIRY_CLICK:
            case AnalyticsService.EV_PRODUCT_VIEW:
            case AnalyticsService.EV_HOMEMADE_STOREFRONT_VIEW:
                analyticsService.record(type, null, null, body.get("kitchenId") != null ? Long.valueOf(body.get("kitchenId")) : null,
                        body.get("detail"));
                break;
            default:
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown event type: " + type));
        }
        return ResponseEntity.ok(Map.of("status", "recorded"));
    }
}
