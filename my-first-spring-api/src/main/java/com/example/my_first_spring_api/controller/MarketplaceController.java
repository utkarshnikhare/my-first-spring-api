package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.MarketplaceDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.SearchResultDto;
import com.example.my_first_spring_api.service.KitchenService;
import com.example.my_first_spring_api.service.MarketplaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final KitchenService kitchenService;

    @Autowired
    public MarketplaceController(MarketplaceService marketplaceService, KitchenService kitchenService) {
        this.marketplaceService = marketplaceService;
        this.kitchenService = kitchenService;
    }

    @GetMapping("/api/marketplace")
    public ResponseEntity<MarketplaceDto> getMarketplaceHome() {
        return ResponseEntity.ok(marketplaceService.getMarketplaceHome());
    }

    @GetMapping("/api/search")
    public ResponseEntity<SearchResultDto> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(kitchenService.search(query));
    }

    @GetMapping("/api/kitchens/{kitchenName}")
    public ResponseEntity<KitchenDetailDto> getKitchenByName(@PathVariable String kitchenName) {
        return ResponseEntity.ok(kitchenService.getKitchenByName(kitchenName));
    }

    @GetMapping("/api/kitchens/{kitchenName}/products")
    public ResponseEntity<List<ProductDto>> getKitchenProducts(@PathVariable String kitchenName) {
        return ResponseEntity.ok(kitchenService.getProductsByKitchenName(kitchenName));
    }

    /** Public kitchen storefront by id (Screen 4) — offerings split Today / Pre-order. */
    @GetMapping("/api/kitchens/id/{id}")
    public ResponseEntity<KitchenDetailDto> getKitchenDetailById(@PathVariable Long id) {
        return ResponseEntity.ok(kitchenService.getKitchenDetailById(id));
    }

    @GetMapping("/api/products/{productId}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(kitchenService.getProductById(productId));
    }

    @GetMapping("/api/kitchens")
    public ResponseEntity<List<KitchenDto>> getAllKitchens() {
        return ResponseEntity.ok(marketplaceService.getAllActiveKitchens());
    }

    /** Global browse: every available menu item across all active kitchens. */
    @GetMapping("/api/items")
    public ResponseEntity<List<ProductDto>> getAllAvailableItems() {
        return ResponseEntity.ok(marketplaceService.getAllAvailableItems());
    }
}
