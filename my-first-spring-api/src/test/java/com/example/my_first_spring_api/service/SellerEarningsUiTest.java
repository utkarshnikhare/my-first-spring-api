package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Screen 8 - Seller Earnings redesign.
 *
 * <p>The screen may be restyled to the approved mockup, but it must keep
 * calling the EXISTING earnings endpoint and keep the app's OWN financial
 * wording (order value grouped by payment status - never settled bank
 * revenue). No figure from the design mock may be hard-coded.</p>
 */
class SellerEarningsUiTest {

    private final String sellerJs = read("static/js/seller.js");
    private final String sellerCss = read("static/css/seller.css");
    private final String buyerJs = read("static/js/buyer.js");

    private static String read(String path) {
        try (var in = SellerEarningsUiTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The redesign must reuse exactly the endpoint the screen always used. */
    @Test
    void earningsReusesTheExistingEarningsApi() {
        assertThat(sellerJs)
                .contains("sellerApi('/api/seller-app/earnings')")
                .as("no second earnings endpoint may be introduced")
                .doesNotContain("/api/seller-app/earnings/")
                .doesNotContain("/api/seller/earnings")
                .doesNotContain("/api/earnings");
    }

    /**
     * The app records order value by payment status. The UI must not claim
     * verified/settled bank revenue the application never records.
     */
    @Test
    void earningsKeepTheAppsOwnFinancialTerminology() {
        assertThat(sellerJs)
                .contains("Confirmed Today")
                .contains("Pending")
                .contains("This Month")
                .as("must not claim verified/settled revenue the app never records")
                .doesNotContain("Verified Revenue")
                .doesNotContain("Settled Revenue")
                .doesNotContain("Bank Revenue")
                .doesNotContain("Earnings Received");
    }

    /**
     * No sample figure or sample offering from the approved mock (Rs 4,000 /
     * Rs 18,850 / Poha / Idli / Thali) may appear as a literal.
     */
    @Test
    void noSampleDataFromTheEarningsMockIsHardCoded() {
        assertThat(sellerJs)
                .doesNotContain("4,000")
                .doesNotContain("18,850")
                .doesNotContain(">Poha<")
                .doesNotContain(">Idli<")
                .doesNotContain(">Thali<");
    }

    /** Existing navigation and the loading/empty/error states survive. */
    @Test
    void earningsKeepsExistingNavigationAndStates() {
        assertThat(sellerJs)
                .as("the full-history link still targets the existing history route")
                .contains("href=\"#/my-offerings\"")
                .as("a skeleton is painted while the payload is in flight")
                .contains("erSkeletonHtml()")
                .as("empty-state copy required by the requirements is preserved")
                .contains("No Earnings")
                .contains("No earnings to show yet")
                .as("API failures stay visible instead of being hidden")
                .contains("Could not load earnings");
    }

    /** The new styles are scoped so no other screen is restyled. */
    @Test
    void theEarningsRedesignIsScopedToItsOwnRoot() {
        assertThat(sellerJs).contains("sd-root er-root");
        assertThat(sellerCss)
                .contains(".er-root")
                .contains("SELLER EARNINGS (SCREEN 8)");
        assertThat(buyerJs).as("buyer is untouched by this phase").doesNotContain("er-root");
    }
}