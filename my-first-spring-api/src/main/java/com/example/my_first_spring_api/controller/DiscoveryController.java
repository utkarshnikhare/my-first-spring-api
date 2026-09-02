package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.DiscoveryDtos.CategoryTile;
import com.example.my_first_spring_api.dto.DiscoveryDtos.ComparisonOffer;
import com.example.my_first_spring_api.dto.DiscoveryDtos.ItemGroup;
import com.example.my_first_spring_api.dto.DiscoveryDtos.KitchenCard;
import com.example.my_first_spring_api.dto.DiscoveryDtos.KitchenCounts;
import com.example.my_first_spring_api.model.Category;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.DiscoveryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Public discovery endpoints backing Screens 2, 2A, 3 & 7.
 * Unauthenticated buyers may browse everything here (deferred-login policy).
 */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final DiscoveryService discoveryService;
    private final BuyerService buyerService;

    @Autowired
    public DiscoveryController(DiscoveryService discoveryService, BuyerService buyerService) {
        this.discoveryService = discoveryService;
        this.buyerService = buyerService;
    }

    /** Dynamic header counts: "8 Live · 3 Tomorrow · 5 Pre-order · 12 All". */
    @GetMapping("/counts")
    public ResponseEntity<KitchenCounts> getCounts() {
        return ResponseEntity.ok(discoveryService.getKitchenCounts());
    }

    /** Kitchen discovery cards; tab = LIVE_NOW (default) | TOMORROW | PREORDER | ALL. */
    @GetMapping("/kitchens")
    public ResponseEntity<List<KitchenCard>> getKitchens(@RequestParam(required = false) String tab,
                                                         HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(discoveryService.getKitchens(tab, buyer));
    }

    /** Category tiles with live item counts (backend also supports SPECIAL). */
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryTile>> getCategories() {
        return ResponseEntity.ok(discoveryService.getCategoryTiles());
    }

    /** Items grouped by dish name; category = BREAKFAST | LUNCH | DINNER | SNACKS | SPECIAL. */
    @GetMapping("/items")
    public ResponseEntity<Map<String, Object>> getItems(@RequestParam(required = false) String category) {
        Category cat = parseCategory(category);
        List<ItemGroup> groups = discoveryService.getItemGroups(cat);
        return ResponseEntity.ok(Map.of(
                "category", category == null ? "ALL" : category.toUpperCase(),
                "count", discoveryService.countItemsInCategory(cat),
                "items", groups));
    }

    /** Kitchens having items in a category — Screen 2A "By Kitchens" grid. */
    @GetMapping("/category-kitchens")
    public ResponseEntity<List<KitchenCard>> getCategoryKitchens(@RequestParam(required = false) String category,
                                                                 HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(discoveryService.getKitchensByCategory(parseCategory(category), buyer));
    }

    /** Live search over dish names (single search entry point, Screen 2). */
    @GetMapping("/search")
    public ResponseEntity<List<ItemGroup>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(discoveryService.searchItemGroups(query));
    }

    /** Screen 7 comparison offers for one dish across kitchens. */
    @GetMapping("/offers")
    public ResponseEntity<Map<String, Object>> getOffers(@RequestParam("item") String itemName,
                                                         HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        List<ComparisonOffer> offers = discoveryService.getComparisonOffers(itemName, buyer);
        return ResponseEntity.ok(Map.of(
                "item", itemName,
                "kitchenCount", offers.size(),
                "offers", offers));
    }

    private Category parseCategory(String category) {
        if (category == null || category.isBlank()) return null;
        try {
            return Category.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
