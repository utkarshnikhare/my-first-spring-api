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
 * Regression cover for the BUYER half of the V1 delivery completion tracker.
 *
 * <p>Delivery is recorded by the SELLER on the shared order row, so the buyer
 * side is read-only: it may only DISPLAY what the backend already returns and
 * must never offer a control that writes delivery state. These checks pin that
 * read-only contract, and pin that the badge is derived from the same order
 * payload as payment and order status - a buyer-specific copy of the flag could
 * silently drift from what the seller actually ticked.</p>
 */
class BuyerDeliveryBadgeStructureTest {

    private static String buyerJs;

    @BeforeAll
    static void loadScript() throws IOException {
        buyerJs = readResource("/js/buyer.js");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = BuyerDeliveryBadgeStructureTest.class.getResourceAsStream(path)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path onDisk = Path.of("src", "main", "resources", "static", path.substring(1));
        return Files.readString(onDisk, StandardCharsets.UTF_8);
    }

    @Test
    void theOrderListShowsTheSellersDeliveryRecordWithoutOfferingAWrite() {
        int start = buyerJs.indexOf("var deliveredBadge = o.deliveryStatus === 'DELIVERED'");
        assertThat(start).as("the order-list row must render the delivery badge").isGreaterThanOrEqualTo(0);
        String card = buyerJs.substring(Math.max(0, start - 900), start + 900);

        assertThat(card)
                .as("delivery is an INDEPENDENT axis: it is read from the order row itself, "
                        + "never inferred from payment or order status")
                .contains("o.deliveryStatus === 'DELIVERED'")
                .contains("Delivered");
        assertThat(card)
                .as("the badge is placed alongside, not instead of, payment and order status")
                .contains("paymentBadge")
                .contains("orderBadge");
        assertThat(card)
                .as("a buyer must never be able to WRITE delivery state")
                .doesNotContain("delivery-status")
                .doesNotContain("mark-all-delivered")
                .doesNotContain("set-delivered");
    }

    @Test
    void theOrderReceiptShowsBothStatesAndTheServerStampedTime() {
        int start = buyerJs.indexOf("<span class=\"cc-label\">Delivery</span>");
        assertThat(start).as("the receipt must show a Delivery row").isGreaterThanOrEqualTo(0);
        String receipt = buyerJs.substring(Math.max(0, start - 400), start + 900);

        assertThat(receipt)
                .as("both states are named - silence must never be mistaken for 'delivered'")
                .contains("o.deliveryStatus === 'DELIVERED'")
                .contains("Delivered")
                .contains("Not delivered yet");
        assertThat(receipt)
                .as("the hand-off time shown is the one the BACKEND stamped")
                .contains("o.deliveredAt")
                .contains("new Date(o.deliveredAt).toLocaleString()");
        assertThat(receipt)
                .as("no browser clock may stand in for the backend's hand-off time")
                .doesNotContain("Date.now()");
    }
}