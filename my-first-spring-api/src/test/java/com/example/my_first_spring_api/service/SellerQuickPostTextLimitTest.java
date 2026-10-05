package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick Post text-input regression guard.
 *
 * <p>Two regressions are guarded here:</p>
 * <ol>
 *   <li>the browser limit drifting away from the authoritative server limit, so
 *       a legitimate post is rejected (or an over-long one is accepted);</li>
 *   <li>a silent-truncation {@code maxlength} that discards the tail of a
 *       pasted post without ever telling the seller.</li>
 * </ol>
 *
 * <p>These read the shipped static/script sources rather than re-testing the
 * service, matching the existing convention in this repository
 * ({@code BuyerHomeOverflowLayoutTest}, {@code SellerAppScriptStructureTest}).</p>
 */
class SellerQuickPostTextLimitTest {

    private static String sellerJs;
    private static String service;

    @BeforeAll
    static void load() throws IOException {
        Path staticDir = Paths.get("src", "main", "resources", "static");
        sellerJs = read(staticDir.resolve("js").resolve("seller.js"));
        service = read(Paths.get("src", "main", "java", "com", "example", "my_first_spring_api",
                "service").resolve("SellerAppService.java"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** The authoritative maximum enforced by {@code createQuickPost}. */
    private static int serverLimit() {
        Matcher m = Pattern.compile("text\\.length\\(\\) > (\\d+)").matcher(service);
        assertThat(m.find()).as("createQuickPost must keep its authoritative length check").isTrue();
        return Integer.parseInt(m.group(1));
    }

    @Test
    void frontendAndBackendAgreeOnTheMaximumLength() {
        Matcher m = Pattern.compile("var QUICK_POST_MAX = (\\d+);").matcher(sellerJs);
        assertThat(m.find()).as("seller.js must declare QUICK_POST_MAX").isTrue();
        assertThat(Integer.parseInt(m.group(1)))
                .as("frontend max length must equal the backend max length")
                .isEqualTo(serverLimit());
    }

    @Test
    void emptyTextIsRejectedOnBothSides() {
        assertThat(sellerJs).as("frontend rejects blank text").contains("if (!message)");
        assertThat(service).as("backend rejects blank text")
                .contains("Quick Post message is required.");
    }

    @Test
    void overLimitTextIsBlockedRatherThanSilentlyTruncated() {
        assertThat(sellerJs)
                .as("maxlength must sit ABOVE the limit so a pasted post is never cut silently")
                .contains("maxlength=\"' + (QUICK_POST_MAX + 1) + '\"");
        assertThat(sellerJs)
                .as("submission must be blocked when over the limit")
                .contains("message.length > QUICK_POST_MAX");
    }

    @Test
    void theSellerCanSeeTheLimitAndTheRemainingCount() {
        assertThat(sellerJs)
                .as("the counter must be rendered and announced to assistive technology")
                .contains("qpCounter")
                .contains("aria-live=\"polite\"")
                .contains("characters left");
    }

    // ==================== Phase 2: Quick Post is an orderable offering ====================

    /**
     * A Quick Post must reach the marketplace through the EXISTING Product
     * creation endpoint. If it ever posts to a bespoke endpoint again it stops
     * being orderable, because only Product rows carry an id that the existing
     * Order button and OrderService can use.
     */
    @Test
    void quickPostIsCreatedThroughTheExistingProductEndpoint() {
        assertThat(sellerJs)
                .as("Quick Post must submit to the shared Product endpoint")
                .contains("/api/seller/products?kitchenId=");
        assertThat(sellerJs)
                .as("Quick Post must no longer create a standalone announcement record")
                .doesNotContain("'/api/seller-app/quick-posts', { method: 'POST'");
    }

    /**
     * The post text is the offering's human-readable content. It must land in
     * Product.description, not be discarded, so buyers actually see it.
     */
    @Test
    void theSellerPostTextBecomesTheOfferingDescription() {
        assertThat(sellerJs)
                .as("the post text must be mapped onto the Product description field")
                .contains("vals.description = message;");
    }

    /**
     * Product requires price, categories, an order cutoff and a ready-by time.
     * Quick Post collects all four instead of inventing marketplace defaults.
     */
    @Test
    void everyRequiredProductFieldIsCollectedNotDefaulted() {
        assertThat(sellerJs)
                .as("Quick Post must collect the fields Product requires")
                .contains("name=\"name\"")
                .contains("name=\"price\"")
                .contains("name=\"priceUnit\"")
                .contains("name=\"orderWindowEnd\"")
                .contains("name=\"readyByTime\"")
                .contains("name=\"categories\"");
        assertThat(sellerJs)
                .as("price must be seller-supplied and positive, never defaulted to zero")
                .contains("if (!isFinite(qpPrice) || qpPrice <= 0)");
        assertThat(sellerJs)
                .as("at least one category must be chosen explicitly")
                .contains("if (qpCategories.length === 0)");
    }

    /** Quick Posts stay Today-only, matching the original announcement rule. */
    @Test
    void quickPostRemainsATodayOnlyOffering() {
        assertThat(sellerJs)
                .contains("vals.availableDate = sellerDate('today');")
                .contains("vals.isPreorder = false;");
    }

    /**
     * Existing quick_posts rows must stay readable. The legacy list is still
     * rendered so previously published announcements are never silently hidden.
     */
    @Test
    void legacyAnnouncementsAreStillRendered() {
        assertThat(sellerJs)
                .as("existing quick_posts must remain visible")
                .contains("sellerApi('/api/seller-app/quick-posts')")
                .contains("Earlier announcements");
    }
}
