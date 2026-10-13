package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for the DELIVERY PROGRESS SUMMARY on the seller's offering
 * drill-down.
 *
 * <p>The Delivery Tracking handover, section 5, requires two numbers at the top
 * of the item/order detail page:</p>
 *
 * <pre>
 *   18 of 32 delivered
 *   14 remaining
 * </pre>
 *
 * <p>Only the first was rendered. The backend already returns
 * {@code remainingCount} alongside {@code deliveredCount} and
 * {@code activeOrderCount}, and the UI already branches on it for the completed
 * state - but a seller could never see how many deliveries were actually left,
 * which is the number the whole checklist exists to drive.</p>
 *
 * <p>This test pins the visible contract: the remaining count must be surfaced,
 * it must come from the server's own figure (never a count of the rows currently
 * rendered, which the filters change), and the completed state must replace it
 * rather than show a misleading "0 remaining".</p>
 */
class SellerDeliveryProgressSummaryTest {

    private static String sellerJs;

    @BeforeAll
    static void loadScript() throws IOException {
        sellerJs = readResource("/js/seller.js");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = SellerDeliveryProgressSummaryTest.class.getResourceAsStream(path)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path onDisk = Path.of("src", "main", "resources", "static", path.substring(1));
        return Files.readString(onDisk, StandardCharsets.UTF_8);
    }

    @Test
    void theProgressSummaryShowsDeliveredAndRemainingSideBySide() {
        int start = sellerJs.indexOf("function deliveryProgressHtml(detail)");
        assertThat(start).as("the delivery progress block must exist").isGreaterThanOrEqualTo(0);
        String block = sellerJs.substring(start, Math.min(sellerJs.length(), start + 1600));

        assertThat(block)
                .as("both handover figures must be visible: delivered AND remaining")
                .contains("' delivered'")
                .contains("' remaining'");

        assertThat(block)
                .as("remaining must be the SERVER's count, not a count of the rendered rows - "
                        + "the filters change the rows without changing what is actually left to deliver")
                .contains("p.remainingCount");

        assertThat(block)
                .as("the denominator must stay the server's active (non-cancelled) order count")
                .contains("p.activeOrderCount")
                .contains("p.deliveredCount");
    }

    @Test
    void theRemainingFigureIsSuppressedOnceEverythingIsDelivered() {
        int start = sellerJs.indexOf("function deliveryProgressHtml(detail)");
        String block = sellerJs.substring(start, Math.min(sellerJs.length(), start + 1600));

        assertThat(block)
                .as("when nothing is left, the completed state replaces a misleading '0 remaining'")
                .contains("remaining === 0")
                .contains("All deliveries complete");

        assertThat(block)
                .as("the remaining suffix is only appended while work is outstanding")
                .contains("remaining > 0 ?");
    }
}
