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
 * Regression cover for the Seller History card responsive defect.
 *
 * <p>Reported symptom: on a phone every offering name rendered one character per
 * line, the card was squeezed into a narrow column, and the card became ~1800px
 * tall. Measured cause was NOT a media query - it reproduced at desktop too:
 * the card is a flex ROW holding three children, and the Republish button carries
 * the shared {@code .btn-block} class whose rule is {@code width:100%}. That
 * demand consumed the whole line, {@code .hc-body} (flex:1 => flex-basis:0,
 * min-width:0) was squeezed to 0px, and {@code overflow-wrap:anywhere} - which
 * also lowers the element's min-content size - then broke the name at every
 * character inside that zero-width box.
 *
 * <p>These tests pin the fixed layout contract so the defect cannot silently
 * return. They assert the stylesheet, which is where the fix lives, and the exact
 * markup seller.js emits, so a change to either side is caught.
 */
class SellerHistoryResponsiveLayoutTest {

    private static String sellerCss;
    private static String sellerJs;
    private static String stylesCss;

    @BeforeAll
    static void loadResources() throws IOException {
        Path css = Paths.get("src", "main", "resources", "static", "css");
        sellerCss = read(css.resolve("seller.css"));
        stylesCss = read(css.resolve("styles.css"));
        sellerJs = read(Paths.get("src", "main", "resources", "static", "js", "seller.js"));
    }

    private static String read(Path p) throws IOException {
        assertThat(p).as("stylesheet/script must exist: %s", p).exists();
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** The rule block for a selector, so a test can assert on one declaration set. */
    private static String rule(String css, String selector) {
        int i = css.indexOf(selector + " {");
        assertThat(i).as("CSS rule for '%s' must exist", selector).isGreaterThanOrEqualTo(0);
        int end = css.indexOf("}", i);
        return css.substring(i, end);
    }

    @Test
    void historyCardWrapsSoTheNameIsNeverSqueezedToZeroWidth() {
        String r = rule(sellerCss, ".history-card");
        // flex-wrap is the actual fix: it gives the .btn-block Republish button its
        // own full-width row so it can no longer demand the whole line.
        assertThat(r).as("the card must wrap its children")
                .contains("flex-wrap: wrap")
                .contains("display: flex");
    }

    @Test
    void historyTextColumnHasAStatedFlexBasisSoItKeepsReadableWidth() {
        String body = rule(sellerCss, ".history-card .hc-body");
        // flex:1 alone means flex-basis:0, so the column only ever receives
        // leftover space - which is exactly what allowed it to reach 0px.
        assertThat(body).as("the body must state a flex basis, not rely on leftovers")
                .contains("flex: 1 1");
        assertThat(body).doesNotContain("flex: 1;");
    }

    @Test
    void offeringNameNeverUsesOverflowWrapAnywhere() {
        assertThat(rule(sellerCss, ".history-card .hc-name"))
                .as("'anywhere' lowers min-content size and allows breaking at every character")
                .doesNotContain("overflow-wrap: anywhere")
                .contains("overflow-wrap: break-word");
        assertThat(rule(sellerCss, ".history-card .hc-meta"))
                .doesNotContain("overflow-wrap: anywhere");
    }

    @Test
    void republishButtonIsScopedAndGetsItsOwnRow() {
        // .btn-block is SHARED with the Buyer screens, so it must keep its global
        // width:100% meaning; only the History card may override it.
        assertThat(rule(stylesCss, ".btn-block"))
                .as("the shared .btn-block rule must be left alone")
                .contains("width: 100%");
        String scoped = rule(sellerCss, ".history-card > .btn-block");
        assertThat(scoped).as("History must override the button to claim a full row")
                .contains("flex: 1 1 100%")
                .contains("width: auto");
    }

    @Test
    void historyPriceStaysOnOneLineAndDoesNotShrink() {
        String price = rule(sellerCss, ".history-card .hc-price");
        assertThat(price).contains("flex: 0 0 auto").contains("white-space: nowrap");
    }

    @Test
    void sellerJsStillEmitsTheThreeCardPartsTheLayoutDependsOn() {
        int start = sellerJs.indexOf("async function sellerHistoryView(");
        assertThat(start).as("sellerHistoryView must exist").isGreaterThanOrEqualTo(0);
        String view = sellerJs.substring(start, sellerJs.indexOf("// SCREEN 4: QUICK POST"));
        assertThat(view).as("the card must keep body, price and Republish children")
                .contains("class=\"history-card\"")
                .contains("class=\"hc-body\"")
                .contains("class=\"hc-name\"")
                .contains("class=\"hc-meta\"")
                .contains("class=\"hc-price\"")
                .contains("btn-block")
                .contains("data-action=\"republish-history\"");
    }

    @Test
    void historyKeepsItsEmptyAndErrorStatesDistinct() {
        int start = sellerJs.indexOf("async function sellerHistoryView(");
        String view = sellerJs.substring(start, sellerJs.indexOf("// SCREEN 4: QUICK POST"));
        // A failed read must not be presented as "no previous items".
        assertThat(view).contains("No previous items").contains("Could not load history");
    }
}
