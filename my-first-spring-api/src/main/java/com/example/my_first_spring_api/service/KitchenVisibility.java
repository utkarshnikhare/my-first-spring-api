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
}
