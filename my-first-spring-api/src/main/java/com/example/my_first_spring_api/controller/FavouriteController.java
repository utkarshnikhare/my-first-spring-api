package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.FavouriteDto;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.FavouriteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OTP-gated favourites (identity-bound action). Returns 401 when the buyer is
 * not authenticated — the SPA intercepts this and opens the deferred login modal.
 */
@RestController
@RequestMapping("/api/favourites")
public class FavouriteController {

    private final FavouriteService favouriteService;
    private final BuyerService buyerService;

    @Autowired
    public FavouriteController(FavouriteService favouriteService, BuyerService buyerService) {
        this.favouriteService = favouriteService;
        this.buyerService = buyerService;
    }

    @GetMapping
    public ResponseEntity<Map<String, List<FavouriteDto>>> getMyFavourites(HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        return ResponseEntity.ok(favouriteService.getFavourites(buyer));
    }

    /** Toggle a kitchen favourite. Body: { "kitchenId": 1 }. */
    @PostMapping("/kitchen/{kitchenId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleKitchen(@PathVariable Long kitchenId, HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        boolean added = favouriteService.toggleKitchen(buyer, kitchenId);
        return ResponseEntity.ok(Map.of("favourited", added));
    }

    /** Toggle a food item favourite. Body: { "productId": 5 }. */
    @PostMapping("/product/{productId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleProduct(@PathVariable Long productId, HttpSession session) {
        User buyer = buyerService.requireCurrentBuyer(session);
        boolean added = favouriteService.toggleProduct(buyer, productId);
        return ResponseEntity.ok(Map.of("favourited", added));
    }
}
