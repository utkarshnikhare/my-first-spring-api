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
 * Regressions for the two Seller "View Orders" filter dropdowns.
 *
 * The filters were wired through the delegated 'click' listener only, which
 * reads the PREVIOUS value of a &lt;select&gt; and re-renders the view — so the
 * freshly selected society/status was overwritten by a stale render (and the
 * native dropdown was re-created mid-interaction). They must be handled by the
 * 'change' listener alone.
 *
 * The society options must also come from the unfiltered payload
 * (detail.availableSocieties), otherwise the option list collapses to the
 * currently selected society and repeated switching stops working.
 */
class SellerFilterScriptStructureTest {

    private static String sellerJs;

    @BeforeAll
    static void loadScript() throws IOException {
        sellerJs = readResource("/js/seller.js");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = SellerFilterScriptStructureTest.class.getResourceAsStream(path)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path onDisk = Path.of("src", "main", "resources", "static", path.substring(1));
        return Files.readString(onDisk, StandardCharsets.UTF_8);
    }

    private static String between(String startMarker, String endMarker) {
        int start = sellerJs.indexOf(startMarker);
        int end = sellerJs.indexOf(endMarker, start + 1);
        assertThat(start).as("marker must exist: " + startMarker).isGreaterThanOrEqualTo(0);
        assertThat(end).as("marker must exist: " + endMarker).isGreaterThan(start);
        return sellerJs.substring(start, end);
    }

    @Test
    void theFilterSelectsAreHandledByChangeOnlyNeverByClick() {
        String clickListener = between("document.addEventListener('click'",
                "document.addEventListener('change'");

        assertThat(clickListener)
                .as("clicking a <select> yields the OLD value; it must not drive the filter")
                .doesNotContain("case 'set-offering-society'")
                .doesNotContain("case 'set-offering-status'")
                .doesNotContain("case 'set-date-calendar'");

        String changeListener = between("document.addEventListener('change'", "// BOOT");
        assertThat(changeListener)
                .as("change must apply the society filter and re-render")
                .contains("data-action=\"set-offering-society\"")
                .contains("S.offeringFilterSociety = societyFilter.value")
                .contains("sellerRender()");
        assertThat(changeListener)
                .as("change must apply the status filter and re-render")
                .contains("data-action=\"set-offering-status\"")
                .contains("S.offeringFilterStatus = statusFilter.value");
    }

    @Test
    void societyOptionsComeFromTheUnfilteredPayloadAndKeepBothDropdowns() {
        int start = sellerJs.indexOf("async function sellerOrderDetailView(");
        int end = sellerJs.indexOf("function offeringCustomersHtml(");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String view = sellerJs.substring(start, end);

        assertThat(view)
                .as("the option list must read the unfiltered society list from the payload")
                .contains("detail.availableSocieties");
        assertThat(view)
                .as("both filter controls keep their data-action attributes")
                .contains("data-action=\"set-offering-society\"")
                .contains("data-action=\"set-offering-status\"")
                .contains("All Societies")
                .contains("All Status")
                .contains("'Paid'")
                .contains("'Pending'")
                .contains("'Cancelled'");
        assertThat(view)
                .as("rows are still rendered from the payload, never queried from the DOM")
                .contains("offeringCustomersHtml(detail)");
    }

    @Test
    void drillDownExplainsTheDashboardBookedFigureBesideItsOwnTotal() {
        int start = sellerJs.indexOf("async function sellerOrderDetailView(");
        int end = sellerJs.indexOf("function offeringCustomersHtml(");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String view = sellerJs.substring(start, end);

        assertThat(view)
                .as("\"N booked\" on the dashboard covers every date of the offering, so the "
                        + "date-scoped drill-down must surface that figure instead of contradicting it")
                .contains("detail.dashboardBookedQuantity")
                .contains("detail.totalPlates");
        assertThat(view)
                .as("the note is only rendered when the two figures genuinely differ")
                .contains("bookedTotal !== (detail.totalPlates || 0)");
    }

    @Test
    void customerRowsStillOpenTheMatchingOrderAndKeepTheEmptyState() {
        int start = sellerJs.indexOf("function offeringCustomersHtml(");
        int end = sellerJs.indexOf("async function sellerOrderDetailByOrderView(");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String rows = sellerJs.substring(start, end);

        assertThat(rows)
                .as("every row carries its order id for navigation")
                .contains("data-action=\"open-order\" data-order=\"");
        assertThat(rows)
                .as("the existing empty state stays in place")
                .contains("No customer orders");

        assertThat(sellerJs)
                .as("clicking a row still routes to that order's detail screen")
                .contains("case 'open-order': sellerNavigate('#/order-detail/order/' + t.dataset.order)");
        assertThat(sellerJs)
                .as("the per-order route still resolves")
                .contains("hash.startsWith('#/order-detail/order/')");
    }
}
