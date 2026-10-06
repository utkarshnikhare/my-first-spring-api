package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Screen 7C - Seller Individual Order detail modernization.
 *
 * <p>There is no separate approved mockup for this drill-down, so it reuses
 * the approved Offering Orders (Screen 7B) visual language instead of
 * inventing a new design. The screen must keep calling the EXISTING single
 * order endpoint, keep both existing actions with their exact hooks and
 * business rules, keep every figure sourced from the payload, and keep an
 * honest loading / error state. The new styles must stay scoped to their own
 * root so Screens 7B/7A/8, Buyer and Admin are untouched.</p>
 */
class SellerIndividualOrderUiTest {

    private final String sellerJs = read("static/js/seller.js");
    private final String sellerCss = read("static/css/seller.css");
    private final String buyerJs = read("static/js/buyer.js");
    private final String adminJs = read("static/js/admin.js");

    private static String read(String path) {
        try (var in = SellerIndividualOrderUiTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The slice of seller.js that renders this screen. */
    private String view() {
        int start = sellerJs.indexOf("async function sellerOrderDetailByOrderView(");
        int end = sellerJs.indexOf("function parseOptionalHhmm(");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return sellerJs.substring(start, end);
    }

    /** The slice of seller.js that renders the loading skeleton. */
    private String skeleton() {
        int start = sellerJs.indexOf("function oicSkeletonHtml()");
        int end = sellerJs.indexOf("async function sellerOrderDetailByOrderView(");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return sellerJs.substring(start, end);
    }

    /** The modernization reuses exactly the endpoint the screen always used. */
    @Test
    void individualOrderReusesTheExistingSingleOrderApi() {
        assertThat(view())
                .contains("sellerApi('/api/seller/orders/' + orderId)");
        assertThat(sellerJs)
                .as("the per-order drill-down route is unchanged")
                .contains("hash.startsWith('#/order-detail/order/')");
    }

    /** Both existing actions keep their exact hooks and business rules. */
    @Test
    void individualOrderKeepsBothExistingActions() {
        String v = view();
        assertThat(v)
                .as("cancel keeps its hook")
                .contains("data-action=\"cancel-order\"")
                .contains("data-order-id=\"' + esc(order.id) + '\"")
                .as("cancel stays limited to live orders")
                .contains("['ORDERED', 'CONFIRMED', 'READY'].indexOf(order.orderStatus) >= 0")
                .as("mark-paid keeps its hook")
                .contains("data-action=\"mark-paid\"")
                .contains("data-oid=\"' + esc(order.id) + '\"")
                .as("mark-paid stays limited to pending non-cancelled orders")
                .contains("(paymentStatus === 'PENDING' || paymentStatus === 'WILL_PAY_LATER') && order.orderStatus !== 'CANCELLED'");
        assertThat(sellerJs)
                .as("the cancel handler still calls the existing status endpoint")
                .contains("'/api/seller/orders/' + encodeURIComponent(t.dataset.orderId) + '/status'")
                .as("the mark-paid handler still calls the existing payment endpoint")
                .contains("'/api/seller/orders/' + oid + '/payment-status'");
    }

    /** No sample figure may be hard-coded; every figure comes from the payload. */
    @Test
    void noSampleDataIsHardCoded() {
        assertThat(view())
                .contains("esc(order.orderNumber)")
                .contains("money(order.totalAmount)")
                .contains("esc(order.buyer.name")
                .contains("item.quantity + 'x ' + money(item.price)")
                .as("the payload carries the dish name as productName, never name")
                .contains("esc(item.productName || item.name)")
                .doesNotContain(">Poha<")
                .doesNotContain("2,880")
                .doesNotContain("30 orders ·");
    }

    /** Loading, error and navigation states survive the modernization. */
    @Test
    void individualOrderKeepsStatesAndNavigation() {
        String v = view();
        assertThat(v)
                .as("a content-shaped skeleton is painted while the payload is in flight")
                .contains("oicSkeletonHtml()")
                .as("back control is preserved")
                .contains("data-action=\"go-back\"")
                .contains("aria-label=\"Back\"")
                .as("error state stays honest with a retry")
                .contains("Could not load details")
                .contains("data-action=\"seller-retry\"");
        assertThat(skeleton())
                .as("the skeleton itself is marked busy for assistive tech")
                .contains("aria-busy=\"true\"")
                .contains("aria-label=\"Loading order details\"");
        assertThat(sellerJs)
                .as("legacy hooks relied on by tooling stay in place")
                .contains("odc-item")
                .contains("odc-buyer-row");
    }

    /** The new styles are scoped so no other screen is restyled. */
    @Test
    void theIndividualOrderRedesignIsScopedToItsOwnRoot() {
        assertThat(sellerJs).contains("sd-root oic-root");
        assertThat(sellerCss)
                .contains(".oic-root")
                .contains("SELLER INDIVIDUAL ORDER (SCREEN 7C)");
        assertThat(buyerJs).as("buyer is untouched by this phase").doesNotContain("oic-root");
        assertThat(adminJs).as("admin is untouched by this phase").doesNotContain("oic-root");
    }
}
