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
     *       exactly as before instead of disappearing. One hole in that path is
     *       closed: a buyer whose only Society is the authoritative ID-backed
     *       reference now resolves by that reference instead of being compared
     *       as "no society at all".</li>
     * </ul>
     *
     * <p><b>Why the two remaining permissive branches are KEPT.</b></p>
     *
     * <p><i>No society on the buyer.</i> Returning {@code true} there looked wrong,
     * but the service is not the thing that guards this. Order placement checks
     * profile completeness first and throws
     * {@code BuyerProfileIncompleteException} for a society-less buyer BEFORE this
     * method is reached, so a society-less buyer is never treated as eligible at
     * the only point where it matters. Meanwhile the draft-before-profile-complete
     * flow deliberately lets such a buyer start a selection, so returning
     * {@code false} here broke a supported flow rather than hardening one.</p>
     *
     * <p><i>No location on the kitchen.</i> A kitchen with no
     * {@code servedSocieties}, no {@code serviceAreas} and no {@code society} is
     * not in this state anywhere in the shipped data, and the repository states no
     * policy for it - it may equally mean "never configured" or "intentionally
     * universal". Rather than guess, the previous behaviour is preserved here and
     * the open question is reported for a product answer.</p>
     */
    public static boolean isServiceAreaVisible(Kitchen kitchen, User buyer) {
        if (kitchen == null) return true;
        // Logged-out browsing: the visitor has no location yet, so every active
        // kitchen stays reachable. Eligibility is enforced when they try to order.
        // This is deliberately NOT tightened - anonymous discovery depends on it,
        // and a buyer who later signs in is re-checked against their Society.
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

        // Legacy string path - still the ONLY thing that keeps an unmigrated kitchen
        // reachable, so its matching stays; only the buyer's Society is now resolved
        // through the authoritative reference first. See the class javadoc.
        String areas = kitchen.getServiceAreas();
        if (areas == null || areas.isBlank()) {
            String society = kitchen.getSociety();
            if (society == null || society.isBlank()) {
                // PHASE B - deliberately UNCHANGED. No shipped kitchen is in this state:
                // every seeded legacy kitchen carries a society name and the migrated
                // ones carry Society IDs, so there is no repository evidence of whether a
                // location-less record means "unconfigured" or "intentionally universal".
                // Closing it would be a policy guess, so the pre-existing permissive
                // behaviour is preserved and the open question is reported instead.
                return true;
            }
            // Match against the buyer's Society, preferring the authoritative ID-backed
            // reference and falling back to the legacy text so a buyer saved with one
            // of the two still resolves. A buyer with neither is still allowed through
            // here: the draft-before-profile-complete flow depends on it, and order
            // PLACEMENT is what actually blocks a society-less buyer, via
            // BuyerProfileIncompleteException in OrderService, before this check runs.
            String buyerSocietyName = buyer.getSociety();
            if ((buyerSocietyName == null || buyerSocietyName.isBlank())
                    && buyer.getSocietyRef() != null) {
                buyerSocietyName = buyer.getSocietyRef().getName();
            }
            if (buyerSocietyName == null || buyerSocietyName.isBlank()) return true;
            return society.equalsIgnoreCase(buyerSocietyName.trim());
        }
        if (buyer.getSociety() == null) return false;
        String[] parts = areas.split(",");
        for (String part : parts) {
            if (part.trim().equalsIgnoreCase(buyer.getSociety())) return true;
        }
        return false;
    }
}
