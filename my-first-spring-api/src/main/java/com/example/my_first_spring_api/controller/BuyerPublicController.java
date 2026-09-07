package com.example.my_first_spring_api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public Buyer routes that must be accessible without authentication.
 * These serve the Buyer SPA (index.html) and rely on client-side hash routing.
 */
@Controller
public class BuyerPublicController {

    /**
     * Shareable direct URL for the Buyer All Kitchens page.
     * Redirects to the SPA hash route so the existing router handles the view.
     */
    @GetMapping("/kitchens")
    public String kitchens() {
        return "redirect:/#/kitchens";
    }
}
