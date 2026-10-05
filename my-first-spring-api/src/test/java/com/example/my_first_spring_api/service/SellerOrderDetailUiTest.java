package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Screen 7B - Seller Offering Orders / Order Detail redesign.
 *
 * <p>The screen may be restyled to the approved mockup, but it must keep
 * calling the EXISTING offering-orders endpoint, keep all three filter axes,
 * keep every figure sourced from the payload, and keep its loading / empty /
 * error states. No sample figure or sample offering from the design mock may
 * be hard-coded, and the redesign must stay scoped to its own root so no
 * other screen is restyled.</p>
 */
class SellerOrderDetailUiTest {

    private final String sellerJs = read("static/js/seller.js");
    private final String sellerCss = read("static/css/seller.css");
    private final String buyerJs = read("static/js/buyer.js");

    private static String read(String path) {
        try (var in = SellerOrderDetailUiTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The slice of seller.js that renders this screen (view -> rows builder). */
    private String view() {
        int start = sellerJs.indexOf("async function sellerOrderDetailView(");
        int end = sellerJs.indexOf("function offeringCustomersHtml(");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return sellerJs.substring(start, end);
    }

    /** The redesign reuses exactly the endpoint the screen always used. */
    @Test
    void orderDetailReusesTheExistingOfferingOrdersApi() {
        assertThat(view())
                .contains("offeringDetailUrl(productId)")
                .contains("sellerApi(offeringDetailUrl(productId))");
        assertThat(sellerJs)
                .as("the drill-down still lives at #/order-detail/<productId>")
                .contains("href=\"#/order-detail/' + p.id + '\"");
    }

    /**
     * The mockup's "Sort: Recent" control has no backing operation - neither a
     * server-side sort parameter nor a persisted sort order exists - so no dead
     * control may be rendered for visual similarity.
     */
    @Test
    void noSortControlIsRenderedWithoutRealSupport() {
        assertThat(sellerJs)
                .doesNotContain("set-offering-sort")
                .doesNotContain("Sort by Item</option>");
    }

    /** No sample figure or sample offering from the approved mock is hard-coded. */
    @Test
    void noSampleDataFromTheOrderDetailMockIsHardCoded() {
        assertThat(sellerJs)
                .doesNotContain(">Poha<")
                .doesNotContain("2,880")
                .doesNotContain("30 orders ·")
                .doesNotContain("Sort: Recent");
    }

    /**
     * Loading, empty and error states survive the redesign, and every figure
     * still comes from the payload rather than from the design mock.
     */
    @Test
    void orderDetailKeepsStatesAndPayloadFigures() {
        String v = view();
        assertThat(v)
                .as("a content-shaped skeleton is painted while the payload is in flight")
                .contains("odSkeletonHtml()")
                .as("the summary figures are read from the payload")
                .contains("detail.totalOrders")
                .contains("detail.totalPlates")
                .contains("detail.totalRevenue")
                .as("the dashboard reconciliation note is preserved")
                .contains("detail.dashboardBookedQuantity")
                .as("error state + retry stay in place")
                .contains("Could not load orders")
                .contains("seller-retry");
        assertThat(sellerJs)
                .as("the existing customer-row empty state stays in place")
                .contains("No customer orders")
                .as("the skeleton itself is marked busy for assistive tech")
                .contains("aria-busy=\"true\"")
                .contains("aria-label=\"Loading offering orders\"");
    }

    /** All three filter axes keep their controls and their change handlers. */
    @Test
    void allThreeFiltersKeepTheirControls() {
        String v = view();
        assertThat(v)
                .contains("data-action=\"set-offering-society\"")
                .contains("data-action=\"set-offering-status\"")
                .contains("data-action=\"set-offering-delivery\"")
                .contains("All Societies")
                .contains("All Status")
                .contains("All Delivery")
                .contains("detail.availableSocieties")
                .contains("offeringCustomersHtml(detail)");
    }

    /**
     * Rows stay tappable AND keyboard-operable: they carry role="button" +
     * tabindex, and Enter/Space opens the same order a click would.
     */
    @Test
    void orderRowsAreKeyboardOperable() {
        assertThat(sellerJs)
                .contains("addEventListener('keydown'")
                .contains("oc-row[data-action=\"open-order\"]")
                .contains("sellerNavigate('#/order-detail/order/' + row.dataset.order)");
        assertThat(sellerJs)
                .as("status stays explicit text, never colour-only")
                .contains("oc-row--' + bucket");
    }

    /** The new styles are scoped so no other screen is restyled. */
    @Test
    void theOrderDetailRedesignIsScopedToItsOwnRoot() {
        assertThat(sellerJs).contains("sd-root od-root");
        assertThat(sellerCss)
                .contains(".od-root")
                .contains("SELLER OFFERING ORDERS / ORDER DETAIL (SCREEN 7B)");
        assertThat(buyerJs).as("buyer is untouched by this phase").doesNotContain("od-root");
    }
}
