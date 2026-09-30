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
 * FIX TWO - Buyer Home horizontal overflow.
 *
 * <p>Measured cause: {@code .tiles-grid} declared {@code grid-template-columns:
 * 1fr 1fr}. A bare {@code 1fr} is {@code minmax(auto, 1fr)}, whose automatic
 * minimum is the item's min-content width. The Home tiles carry a {@code .pill}
 * badge with {@code white-space: nowrap}, so each track was floored at ~217px
 * instead of the ~157px available. The grid overflowed, {@code documentElement
 * .scrollWidth} grew to 412px, and the fixed bottom nav stretched to match -
 * 92px of horizontal scroll at 320px and 52px at 360px.
 *
 * <p>The fix is a real layout correction, not {@code overflow-x: hidden}: the
 * tracks use {@code minmax(0, 1fr)}, the tile may shrink, and the badge wraps.
 * These tests also assert the labels are still VISIBLE, so a future change that
 * merely hides the scroll while clipping content would fail.
 */
class BuyerHomeOverflowLayoutTest {

    private static String stylesCss;
    private static String buyerJs;

    @BeforeAll
    static void load() throws IOException {
        Path css = Paths.get("src", "main", "resources", "static", "css");
        stylesCss = new String(Files.readAllBytes(css.resolve("styles.css")), StandardCharsets.UTF_8);
        buyerJs = new String(Files.readAllBytes(Paths.get("src", "main", "resources", "static", "js", "buyer.js")),
                StandardCharsets.UTF_8);
    }

    private static String rule(String selector) {
        int i = stylesCss.indexOf(selector + " {");
        assertThat(i).as("CSS rule for '%s' must exist", selector).isGreaterThanOrEqualTo(0);
        return stylesCss.substring(i, stylesCss.indexOf("}", i));
    }

    @Test
    void tilesGridTracksCanShrinkBelowTheItemMinContentWidth() {
        String r = rule(".tiles-grid");
        assertThat(r)
                .as("a bare `1fr` floors the track at min-content - the actual root cause")
                .doesNotContain("grid-template-columns: 1fr 1fr")
                .contains("grid-template-columns: minmax(0, 1fr) minmax(0, 1fr)");
    }

    @Test
    void theTileItselfIsAllowedToShrink() {
        assertThat(rule(".tile"))
                .as("a flex child defaults to min-width:auto, which would keep it wide")
                .contains("min-width: 0");
    }

    @Test
    void theTileBadgeWrapsInsteadOfHoldingTheColumnOpen() {
        // Scoped to .tile so every other .pill on the app keeps nowrap.
        assertThat(rule(".tile .pill"))
                .contains("white-space: normal");
        assertThat(rule(".pill"))
                .as("the shared .pill rule is unchanged for every other consumer")
                .contains("white-space: nowrap");
    }

    @Test
    void tileLabelsCanWrapRatherThanOverflow() {
        assertThat(rule(".tile-name"))
                .contains("overflow-wrap: break-word")
                .contains("word-break: normal");
    }

    @Test
    void theFixIsALayoutCorrectionNotAHiddenScroll() {
        // NOTE: `body { overflow-x: hidden; overflow-x: clip }` already existed at
        // HEAD and is NOT part of this fix. It never suppressed the symptom - the
        // measurement showed documentElement.scrollWidth 412 with that rule in
        // place, because the fixed bottom nav still expanded the document. This
        // test therefore guards the elements this fix actually touches: none of
        // them may clip, so content has to genuinely fit rather than be hidden.
        for (String selector : new String[]{".tiles-grid", ".tile", ".tile .pill", ".tile-name"}) {
            String r = rule(selector);
            assertThat(r).as("%s must not clip the overflow it just stopped causing", selector)
                    .doesNotContain("overflow-x")
                    .doesNotContain("overflow:");
        }
    }

    @Test
    void buyerHomeStillRendersTheTwoModuleTiles() {
        // The correction is scoped; the Home content itself is untouched.
        assertThat(buyerJs).contains("tiles-grid").contains("Food &amp; Kitchens")
                .contains("Homemade Products");
    }

    @Test
    void otherBuyerGridsKeepTheirExistingColumns() {
        // Scope guard: only .tiles-grid was changed, the other 1fr grids are as before.
        assertThat(rule(".items-grid")).contains("grid-template-columns: 1fr 1fr");
        assertThat(rule(".cc-grid")).contains("grid-template-columns: 1fr 1fr");
    }
}
