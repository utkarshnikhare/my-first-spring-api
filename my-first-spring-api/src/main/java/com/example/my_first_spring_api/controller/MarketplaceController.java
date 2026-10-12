package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.MarketplaceDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.SearchResultDto;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.KitchenService;
import com.example.my_first_spring_api.service.MarketplaceService;
import com.example.my_first_spring_api.service.SlugService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@RestController
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final KitchenService kitchenService;
    private final BuyerService buyerService;
    private final SlugService slugService;

    @Autowired
    public MarketplaceController(MarketplaceService marketplaceService, KitchenService kitchenService,
                                 BuyerService buyerService, SlugService slugService) {
        this.marketplaceService = marketplaceService;
        this.kitchenService = kitchenService;
        this.buyerService = buyerService;
        this.slugService = slugService;
    }

    @GetMapping("/api/marketplace")
    public ResponseEntity<MarketplaceDto> getMarketplaceHome(HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(marketplaceService.getMarketplaceHome(buyer));
    }

    @GetMapping("/api/search")
    public ResponseEntity<SearchResultDto> search(@RequestParam("q") String query, HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(kitchenService.search(query, buyer));
    }

    /**
     * Suggest a unique public-URL slug from a Kitchen Name. The URL is derived
     * server-side (never only in the browser); the returned slug is already
     * de-duplicated with a -2/-3 suffix against every existing slug and alias.
     */
    @GetMapping("/api/kitchens/slug-suggestion")
    public ResponseEntity<Map<String, Object>> slugSuggestion(@RequestParam("name") String name) {
        String slug = slugService.suggestUnique(name);
        return ResponseEntity.ok(Map.of("name", name == null ? "" : name, "slug", slug));
    }

    /** Check whether a specific slug is available for a new kitchen. */
    @GetMapping("/api/kitchens/slug-check")
    public ResponseEntity<Map<String, Object>> slugCheck(@RequestParam("slug") String slug) {
        boolean taken = slugService.isTaken(slug);
        return ResponseEntity.ok(Map.of("slug", slug == null ? "" : slug, "available", !taken));
    }

    /**
     * Public kitchen storefront resolved from its public-URL slug. Returns the
     * same KitchenDetailDto as the id-based page; the buyer router reads the slug
     * from {@code /kitchen/{slug}} and calls this. Status-based visibility is
     * enforced server-side in KitchenService.
     */
    @GetMapping("/api/kitchens/by-slug/{slug}")
    public ResponseEntity<KitchenDetailDto> getKitchenBySlug(@PathVariable String slug, HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(kitchenService.getKitchenDetailBySlug(slug, buyer));
    }

    @GetMapping("/api/kitchens/{kitchenName}")
    public ResponseEntity<KitchenDetailDto> getKitchenByName(@PathVariable String kitchenName, HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(kitchenService.getKitchenByName(kitchenName, buyer));
    }

    @GetMapping("/api/kitchens/{kitchenName}/products")
    public ResponseEntity<List<ProductDto>> getKitchenProducts(@PathVariable String kitchenName, HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(kitchenService.getProductsByKitchenName(kitchenName, buyer));
    }

    /** Public kitchen storefront by id (Screen 4) — offerings split Today / Pre-order. */
    @GetMapping("/api/kitchens/id/{id}")
    public ResponseEntity<KitchenDetailDto> getKitchenDetailById(@PathVariable Long id, HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(kitchenService.getKitchenDetailById(id, buyer));
    }

    @GetMapping("/api/products/{productId}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Long productId, HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(kitchenService.getProductById(productId, buyer));
    }

    @GetMapping("/api/kitchens")
    public ResponseEntity<List<KitchenDto>> getAllKitchens(HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(marketplaceService.getAllActiveKitchens(buyer));
    }

    /** Global browse: every available menu item across all active kitchens. */
    @GetMapping("/api/items")
    public ResponseEntity<List<ProductDto>> getAllAvailableItems(HttpSession session) {
        User buyer = buyerService.getCurrentBuyer(session);
        return ResponseEntity.ok(marketplaceService.getAllAvailableItems(buyer));
    }
}
