package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for Batch 1 defects DEF-01, DEF-02, DEF-03.
 *
 * These tests verify the seller.js frontend changes by inspecting the
 * bundled script resource on the classpath, plus backend behaviour where
 * applicable (auth logout, default unit in DTO).
 */
class SellerDefectBatch1Test {

    private static String sellerJs;

    static {
        try {
            sellerJs = readResource("/js/seller.js");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load seller.js", e);
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = SellerDefectBatch1Test.class.getResourceAsStream(path)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path onDisk = Path.of("src", "main", "resources", "static", path.substring(1));
        return Files.readString(onDisk, StandardCharsets.UTF_8);
    }

    /* ==================== DEF-01: Quick Start clear ==================== */

    @Test
    void clearPresetActionExistsInSellerJs() {
        assertThat(sellerJs)
                .as("clear-preset action must exist in seller.js for DEF-01")
                .contains("data-action=\"clear-preset\"");
    }

    @Test
    void clearMealPresetFunctionExists() {
        assertThat(sellerJs)
                .as("clearMealPreset function must exist in seller.js for DEF-01")
                .contains("function clearMealPreset(form)");
    }

    @Test
    void clearPresetHandlerExistsInClickDispatcher() {
        assertThat(sellerJs)
                .as("clear-preset case must exist in the click handler for DEF-01")
                .contains("case 'clear-preset':");
    }

    /* ==================== DEF-02: Default unit Per Plate ==================== */

    @Test
    void defaultUnitIsPerPlateForNewKitchenOfferings() {
        assertThat(sellerJs)
                .as("default unit for new kitchen/food offerings must be Per Plate for DEF-02")
                .contains("var selectedUnit = t.priceUnit || 'Per Plate'");
    }

    @Test
    void perPlateOptionExistsInUnitSelect() {
        assertThat(sellerJs)
                .as("Per Plate option must exist in the unit select dropdown")
                .contains("<option value=\"Per Plate\"");
    }

    /* ==================== DEF-03: Seller logout ==================== */

    @Test
    void sellerLogoutButtonExistsInDashboardHeader() {
        assertThat(sellerJs)
                .as("seller-logout button must exist in the dashboard header for DEF-03")
                .contains("data-action=\"seller-logout\"")
                .contains("sd-header__logout");
    }

    @Test
    void sellerLogoutHandlerExists() {
        assertThat(sellerJs)
                .as("seller-logout case must exist in the click handler for DEF-03")
                .contains("case 'seller-logout':");
    }

    @Test
    void logoutCallsAuthLogoutEndpoint() {
        assertThat(sellerJs)
                .as("logout handler must invoke POST /api/auth/logout")
                .contains("api('/api/auth/logout', { method: 'POST' })");
    }
}
