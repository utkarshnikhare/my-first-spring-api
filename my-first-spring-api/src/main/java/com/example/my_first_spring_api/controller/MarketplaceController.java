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

    @GetMapping("/api/products/{productId}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(kitchenService.getProductById(productId));
    }

    @GetMapping("/api/kitchens")
    public ResponseEntity<List<KitchenDto>> getAllKitchens() {
        MarketplaceDto home = marketplaceService.getMarketplaceHome();
        return ResponseEntity.ok(home.getKitchens());
    }
}
