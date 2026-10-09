package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seller Dashboard UI redesign - regression guard.
 *
 * <p>The dashboard was redesigned to match the approved Stitch reference. This
 * suite pins the rules that must survive that redesign, so a future visual tweak
 * cannot quietly regress working functionality:</p>
 * <ul>
 *   <li>the redesign is presentation-only - no new endpoint, no new route;</li>
 *   <li>every existing dashboard action (View Orders / Edit / Pause / Resume /
 *       Sold Out / Add Offering / Kitchen Preview) is still wired;</li>
 *   <li>no sample data from the design mock is hard-coded;</li>
 *   <li>the new styles are scoped so they cannot restyle Buyer/Admin.</li>
 * </ul>
 *
 * <p>Static-source assertions follow the existing convention in this repository
 * ({@code SellerKitchenPreviewTest}, {@code SellerQuickPostTextLimitTest}).</p>
 */
class SellerDashboardUiTest {

    private static String sellerJs;
    private static String sellerCss;
    private static String sellerHtml;
    private static String buyerJs;
    private static String adminJs;

    @BeforeAll
    static void load() throws IOException {
        Path staticDir = Paths.get("src", "main", "resources", "static");
        sellerJs = read(staticDir.resolve("js").resolve("seller.js"));
        buyerJs = read(staticDir.resolve("js").resolve("buyer.js"));
        adminJs = read(staticDir.resolve("js").resolve("admin.js"));
        sellerCss = read(staticDir.resolve("css").resolve("seller.css"));
        sellerHtml = read(staticDir.resolve("seller.html"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** The dashboard must render the approved section structure. */
    @Test
    void theDashboardRendersTheApprovedSectionStructure() {
        assertThat(sellerJs)
                .as("brand header")
                .contains("class=\"sd-header__word\">SocioMart<")
                .as("dynamic greeting")
                .contains("sd-greet__hi")
                .as("three summary cards")
                .contains("Views Today").contains("Followers").contains("Total Orders")
                .as("store profile card")
                .contains("sd-store")
                .as("quick actions")
                .contains("View Store").contains("Manage Profile").contains("Gallery")
                .as("offerings section with dynamic count")
                .contains("sd-section__count")
                .as("offering card")
                .contains("sd-card")
                .as("add offering CTA")
                .contains("sd-add")
                .as("earnings summary")
                .contains("Earnings Summary")
                .as("loading + empty + error states")
                .contains("sdSkeletonHtml()")
                .contains("No Offerings yet")
                .contains("Could not load dashboard");
    }

    /**
     * The whole redesign must reuse the endpoints the Seller app already used.
     * A new dashboard-specific endpoint would be a functional change, not a
     * presentation change.
     */
    @Test
    void theDashboardReusesOnlyExistingSellerApis() {
        assertThat(sellerJs)
                .as("metrics + offerings + earnings still come from the dashboard API")
                .contains("sellerApi('/api/seller-app/dashboard')")
                .as("store photo/name/status still come from the existing kitchen API")
                .contains("sellerApi('/api/seller/kitchen')")
                .as("per-offering counts reuse the existing order-summary API")
                .contains("sellerApi('/api/seller-app/orders/summary?date='");
        assertThat(sellerJs)
                .as("no bespoke dashboard endpoint may be introduced")
                .doesNotContain("/api/seller-app/dashboard-stats")
                .doesNotContain("/api/seller/dashboard")
                .doesNotContain("/api/dashboard");
    }

    /**
     * No sample figure from the design mock may be hard-coded. The mock showed
     * Aarti / Poha / Idli / 30 rupees / "20 booked" / "11:00 AM" - all of which
     * must come from real data instead.
     */
    @Test
    void noSampleDataFromTheDesignMockIsHardCoded() {
        assertThat(sellerJs)
                .as("no sample seller/offering names from the mock")
                .doesNotContain(">Aarti<").doesNotContain(">Poha<").doesNotContain(">Idli<")
                .doesNotContain(">Modak<").doesNotContain(">Misal Pav<").doesNotContain(">Puran Poli<")
                .as("the greeting must be built from the real seller name, not a literal")
                .doesNotContain("Good afternoon, Aarti")
                .doesNotContain("Good morning, Aarti")
                .doesNotContain("Good evening, Aarti");
    }

    /** Every operational action the seller relies on must still be present. */
    @Test
    void allExistingOfferingActionsRemainWired() {
        assertThat(sellerJs)
                .as("View Orders still targets the existing per-offering order route")
                .contains("href=\"#/order-detail/' + p.id + '\"")
                .as("Edit still uses the existing edit action")
                .contains("data-action=\"edit-offering\"")
                .as("Pause stays reversible via the existing resume action")
                .contains("data-action=\"pause-orders\"")
                .contains("data-action=\"resume-orders\"")
                .as("Sold Out remains a separate action from Pause")
                .contains("data-action=\"mark-soldout\"")
                .as("Add Offering still enters the existing create flow")
                .contains("data-action=\"go-add\"");
        // V3 §11 + acceptance checklist: "Quantity +/- stepper is removed from
        // dashboard." The earlier pin (stepper preserved) is superseded by the
        // approved V3 change request, so the pin is inverted: the dashboard
        // card may never grow an inline stock stepper again.
        assertThat(sellerJs)
                .as("no inline quantity stepper may be rendered on dashboard cards")
                .doesNotContain("sd-card__stepper")
                .doesNotContain("data-action=\"inv-inc\"")
                .doesNotContain("data-action=\"inv-dec\"");
    }

    /**
     * V2 §8 / V3 §8: a recurring offering on the LIVE tab must offer
     * occurrence-scoped actions (Edit Today / Sold Out Today / Close Orders
     * Today) instead of the generic one-time controls, and Recurring must be
     * a card badge rather than a dashboard tab.
     */
    @Test
    void recurringLiveCardsUseOccurrenceActionsAndBadges() {
        assertThat(sellerJs)
                .as("Edit Today / Edit This Date operate on one occurrence")
                .contains("data-action=\"edit-today\"")
                .contains("data-action=\"edit-this-date\"")
                .as("per-day Sold Out / Close Orders actions hit the occurrence routes")
                .contains("data-action=\"occ-sold-out\"")
                .contains("data-action=\"occ-close\"")
                .contains("/sold-out'")
                .contains("/pause'")
                .as("RECURRING is a badge on the card")
                .contains("RECURRING • LIVE")
                .as("an occurrence edit can never write the product or the rule")
                .contains("S.editOccurrenceId")
                .contains("/api/seller/schedules/occurrences/' + occId, { method: 'PATCH'");
    }

    /**
     * V3 §3.1: the complete Total Orders card opens the existing Orders
     * screen; V2 §15: a capped Ongoing schedule prompts the seller to extend.
     */
    @Test
    void totalOrdersCardIsClickableAndOngoingSchedulesPromptToExtend() {
        assertThat(sellerJs)
                .as("Total Orders card links to the existing Orders screen")
                .contains("label: 'Total Orders', href: '#/orders'")
                .as("near-expiry Ongoing schedules prompt for extension")
                .contains("data-action=\"extend-schedule\"")
                .contains("This ongoing schedule ends")
                .contains("/extend'")
                .as("new ongoing schedules must tell the server to own the 90-day horizon")
                .contains("ongoing: duration === 'ongoing'")
                .as("Manage Schedule must understand serialized DayOfWeek names")
                .contains("MONDAY: 1").contains("SUNDAY: 7");
        assertThat(sellerJs)
                .as("the removed quick-action row must not leave loading placeholders behind")
                .doesNotContain("sd-skel--qa");
    }

    /**
     * Pause, Sold Out and Orders Closed must stay three distinct states - the
     * redesign must not collapse them into one badge.
     */
    @Test
    void offeringStatesRemainDistinct() {
        assertThat(sellerJs)
                .contains("oc-badge soldout\">SOLD OUT")
                .contains("oc-badge paused\">PAUSED")
                .contains("oc-badge closed\">ORDERS CLOSED")
                .contains("oc-badge live\">LIVE");
        assertThat(sellerJs)
                .as("a paused offering must offer Resume, never Sold Out")
                .contains("p.ordersPaused && !p.soldOut")
                .as("LIVE on the store card must be derived from the real paused flag")
                .contains("var paused = !!(kitchen && kitchen.paused);");
    }

    /** The recently fixed Kitchen Preview must not regress. */
    @Test
    void kitchenPreviewStillWorksFromTheRedesignedDashboard() {
        assertThat(sellerJs)
                .as("View Store on the dashboard reuses the existing preview action")
                .contains("data-action=\"preview-kitchen\"")
                .as("the preview still opens the existing buyer kitchen route")
                .contains("location.href = '/index.html#/kitchen/' + encodeURIComponent(previewId);")
                .as("the Kitchen screen keeps its own Preview button too")
                .contains("data-action=\"preview-kitchen\">Preview Kitchen Page</button>");
    }

    /** The redesign must stay confined to the Seller app. */
    @Test
    void theRedesignIsScopedToTheSellerAppOnly() {
        assertThat(buyerJs).as("buyer is untouched by this phase").doesNotContain("sd-root");
        assertThat(adminJs).as("admin is untouched by this phase").doesNotContain("sd-root");
        assertThat(sellerCss)
                .as("new styles live in the seller stylesheet only")
                .contains(".sd-root")
                .as("the new block is scoped so shared components are untouched")
                .contains("SELLER DASHBOARD - Stitch visual system");
    }

    /**
     * Bottom navigation keeps its five existing routes; only the visible label
     * follows the design ("Store" instead of "Kitchen").
     */
    @Test
    void bottomNavigationKeepsItsFiveExistingRoutes() {
        assertThat(sellerHtml)
                .contains("href=\"#/home\"").contains("href=\"#/kitchen\"")
                .contains("href=\"#/orders\"").contains("href=\"#/my-offerings\"")
                .contains("href=\"#/earnings\"")
                .as("design label applied without changing the route")
                .contains(">Store</span>")
                .as("the selected tab keeps its active state hook")
                .contains("data-nav=\"home\"");
    }

    /** Earnings must use the app's own wording, not claim settled bank revenue. */
    @Test
    void earningsUseExistingFinancialTerminology() {
        assertThat(sellerJs)
                .as("the app records order value by payment status")
                .contains("Confirmed Today").contains("Pending").contains("This Month")
                .as("must not claim verified/settled revenue the app never records")
                .doesNotContain("Verified Revenue")
                .as("View Details opens the existing Earnings screen")
                .contains("href=\"#/earnings\"");
    }
}
