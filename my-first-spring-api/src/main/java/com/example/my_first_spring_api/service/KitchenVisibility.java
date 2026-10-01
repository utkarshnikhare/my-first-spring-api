package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerType;
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
        if (kitchen == null) return false;
        User seller = kitchen.getSeller();
        return seller != null
                && seller.getRole() == UserRole.SELLER
                && seller.isApprovedSeller()
                && Boolean.TRUE.equals(kitchen.getAvailableToday());
    }

    public static boolean isPaused(Kitchen kitchen) {
        if (kitchen == null) return false;
        User seller = kitchen.getSeller();
        return seller != null
                && seller.getRole() == UserRole.SELLER
                && seller.isApprovedSeller()
                && Boolean.FALSE.equals(kitchen.getAvailableToday());
    }

    public static boolean isHomemadeStore(Kitchen kitchen) {
        return kitchen != null && kitchen.getSellerType() == SellerType.HOMEMADE_PRODUCTS;
    }

    /**
     * "May this buyer see / order from this kitchen?"
     *
     * <p>One rule for every marketplace entry point (Home, Food &amp; Kitchens,
     * discovery, search, favourites, enquiries, kitchen detail) and for order
     * placement - so the UI can never be the security boundary.</p>
     *
     * <ul>
     *   <li><b>No buyer</b> (logged-out visitor) - everything active is browsable.
     *       Ordering still has to pass authentication, location capture and this
     *       same check once a Society is known.</li>
     *   <li><b>Kitchen has explicit Society coverage</b> - the buyer's saved
     *       Society must be one of them, compared by ID. A buyer with no valid
     *       saved Society is not eligible. This is the authoritative path.</li>
     *   <li><b>Kitchen has no ID coverage yet</b> (legacy record whose society
     *       strings could not be mapped unambiguously) - the original string
     *       comparison is kept so existing, already-working configurations behave
     *       exactly as before instead of disappearing.</li>
     * </ul>
     */
    public static boolean isServiceAreaVisible(Kitchen kitchen, User buyer) {
        if (kitchen == null) return true;
        // Logged-out browsing: the visitor has no location yet, so every active
        // kitchen stays reachable. Eligibility is enforced when they try to order.
        if (buyer == null) return true;

        java.util.Set<com.example.my_first_spring_api.model.Society> coverage = kitchen.getServedSocieties();
        if (coverage != null && !coverage.isEmpty()) {
            com.example.my_first_spring_api.model.Society buyerSociety = buyer.getSocietyRef();
            if (buyerSociety == null || buyerSociety.getId() == null) return false;
            for (com.example.my_first_spring_api.model.Society covered : coverage) {
                if (covered != null && buyerSociety.getId().equals(covered.getId())) return true;
            }
            return false;
        }

        // Legacy string path - unchanged behaviour for unmigrated records.
        String areas = kitchen.getServiceAreas();
        if (areas == null || areas.isBlank()) {
            String society = kitchen.getSociety();
            if (society == null || society.isBlank()) return true;
            if (buyer.getSociety() == null) return true;
            return society.equalsIgnoreCase(buyer.getSociety());
        }
        if (buyer.getSociety() == null) return true;
        String[] parts = areas.split(",");
        for (String part : parts) {
            if (part.trim().equalsIgnoreCase(buyer.getSociety())) return true;
        }
        return false;
    }
}
