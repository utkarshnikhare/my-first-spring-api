package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;

/**
 * Single source of truth for "is this kitchen publicly active on the platform?".
 * A kitchen is only visible to buyers when its owner is an APPROVED seller.
 * Used by the marketplace, kitchen detail, search, and draft-order guards.
 */
public final class KitchenVisibility {

    private KitchenVisibility() {}

    public static boolean isPubliclyVisible(Kitchen kitchen) {
        User seller = kitchen.getSeller();
        return seller != null
                && seller.getRole() == UserRole.SELLER
                && seller.isApprovedSeller();
    }

    public static boolean isServiceAreaVisible(Kitchen kitchen, User buyer) {
        if (kitchen == null) return true;
        String areas = kitchen.getServiceAreas();
        if (areas == null || areas.isBlank()) {
            String society = kitchen.getSociety();
            if (society == null || society.isBlank()) return true;
            if (buyer == null || buyer.getSociety() == null) return true;
            return society.equalsIgnoreCase(buyer.getSociety());
        }
        if (buyer == null || buyer.getSociety() == null) return true;
        String[] parts = areas.split(",");
        for (String part : parts) {
            if (part.trim().equalsIgnoreCase(buyer.getSociety())) return true;
        }
        return false;
    }
}
