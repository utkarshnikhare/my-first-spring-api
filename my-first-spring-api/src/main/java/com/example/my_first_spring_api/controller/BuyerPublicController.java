package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.service.SlugService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Public Buyer routes that must be accessible without authentication.
 *
 * <p>These serve the Buyer SPA ({@code index.html}) and rely on the client
 * router reading {@code location.pathname} for the clean routes
 * {@code /kitchen/{slug}} and {@code /kitchens}. A forward (not a redirect) is
 * used so a deep link or a page refresh lands directly on the SPA, which then
 * resolves the slug itself.</p>
 */
@Controller
public class BuyerPublicController {

    private final SlugService slugService;

    @Autowired
    public BuyerPublicController(SlugService slugService) {
        this.slugService = slugService;
    }

    /**
     * Shareable direct URL for the Buyer All Kitchens page (marketplace).
     * Serves the SPA; the client router renders the existing kitchens view.
     */
    @GetMapping("/kitchens")
    public String kitchens() {
        return "forward:/index.html";
    }

    /**
     * Clean public kitchen URL. If the slug is a retired alias (an Admin changed
     * the slug), 301-redirect to the canonical {@code /kitchen/{current-slug}}
     * so every already-shared link keeps working. Otherwise serve the SPA and let
     * the client resolve the slug.
     */
    @GetMapping("/kitchen/{slug}")
    public String kitchen(@PathVariable String slug, HttpServletRequest request) {
        String canonical = slugService.canonicalSlugForPublic(slug, request);
        if (canonical != null && !canonical.equals(slug)) {
            return "redirect:" + (canonical.startsWith("/") ? canonical : "/" + canonical);
        }
        return "forward:/index.html";
    }

    /**
     * Shareable direct URL for Buyer Homemade Products hub.
     */
    @GetMapping({"/homemade", "/homemade/"})
    public String homemade() {
        return "forward:/index.html";
    }

    /**
     * Clean public Homemade Store URL. Serves the SPA; the client router resolves the slug.
     */
    @GetMapping("/homemade-store/{slug}")
    public String homemadeStore(@PathVariable String slug, HttpServletRequest request) {
        String canonical = slugService.canonicalSlugForPublic(slug, request);
        if (canonical != null && !canonical.equals(slug)) {
            return "redirect:/homemade-store/" + canonical;
        }
        return "forward:/index.html";
    }
}
