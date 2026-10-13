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
 * Regressions for the three Seller "View Orders" filter dropdowns and for the
 * delivery controls that sit on the same screen.
 *
 * The filters were wired through the delegated 'click' listener only, which
 * reads the PREVIOUS value of a &lt;select&gt; and re-renders the view — so the
 * freshly selected society/status was overwritten by a stale render (and the
 * native dropdown was re-created mid-interaction). They must be handled by the
 * 'change' listener alone. The delivery dropdown is a THIRD independent axis
 * and must obey the same rule.
 *
 * The society options must also come from the unfiltered payload
 * (detail.availableSocieties), otherwise the option list collapses to the
 * currently selected society and repeated switching stops working.
 *
 * <p>The delivery tracker adds a per-row Delivered checkbox (auto-save) and a
 * bulk "Mark All Delivered" action. Both have a read-only counterpart in the
 * progress block, so the checks below also pin the contract that the numbers
 * on screen are always the SERVER's, never a client-side adjustment.</p>
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

    // ------------------------------------------------------------------
    // V1 delivery completion tracker - the two NEW controls on this screen.
    // ------------------------------------------------------------------

    @Test
    void theDeliveryFilterIsAThirdIndependentAxisAndIsReadFromChangeOnly() {
        String clickListener = between("document.addEventListener('click'",
                "document.addEventListener('change'");
        assertThat(clickListener)
                .as("a <select> click reports the OLD option; it must not drive any filter")
                .doesNotContain("case 'set-offering-delivery'");

        String changeListener = between("document.addEventListener('change'", "// BOOT");
        assertThat(changeListener)
                .as("delivery must COMBINE with society + payment, not replace them")
                .contains("e.target.closest('[data-action=\"set-offering-delivery\"]')")
                .contains("S.offeringFilterDelivery = deliveryFilter.value")
                .contains("sellerRender()");

        // All three axes travel in the SAME query string, so no filter can be
        // silently dropped when another one is applied.
        String url = between("function offeringDetailUrl(", "async function sellerOrderDetailView(");
        assertThat(url)
                .as("the drill-down URL carries the delivery axis next to the other two")
                .contains("'&delivery=' + encodeURIComponent(S.offeringFilterDelivery)");

        int start = sellerJs.indexOf("async function sellerOrderDetailView(");
        int end = sellerJs.indexOf("function offeringCustomersHtml(");
        String view = sellerJs.substring(start, end);
        assertThat(view)
                .as("the third dropdown renders with both concrete states")
                .contains("data-action=\"set-offering-delivery\"")
                .contains("All Delivery")
                .contains("'delivered'")
                .contains("'not_delivered'");
    }

    @Test
    void theRowDeliveredCheckboxAutoSavesFromChangeAndIsNeverAWriteFromClick() {
        // The checkbox sits INSIDE a row that navigates on click. Reading it from
        // 'click' would (a) report the pre-toggle value and (b) open the order
        // detail screen instead of saving, so it must be change-only.
        assertThat(sellerJs)
                .as("the click listener bails out before it can navigate the row away")
                .contains("if (a === 'set-delivered') return;");

        String changeListener = between("document.addEventListener('change'", "// BOOT");
        assertThat(changeListener)
                .as("only the checkbox itself drives the auto-save")
                .contains("e.target.closest('[data-action=\"set-delivered\"]')")
                .contains("deliveredToggle.type === 'checkbox'")
                .contains("saveDeliveryToggle(deliveredToggle)");

        int start = sellerJs.indexOf("async function saveDeliveryToggle(");
        int end = sellerJs.indexOf("// SCREEN 7C: INDIVIDUAL ORDER DETAIL");
        String save = sellerJs.substring(start, end);
        assertThat(save)
                .as("both directions are saved against the shared order row")
                .contains("'/delivery-status'")
                .contains("method: 'PATCH'")
                .contains("wantDelivered ? 'DELIVERED' : 'NOT_DELIVERED'");
        assertThat(save)
                .as("a failed save must roll the checkbox back to the last SERVER-confirmed value")
                .contains("input.checked = wasDelivered")
                .contains("toast(err.message, 'error')");
        assertThat(save)
                .as("the counts on screen are re-read, never adjusted in the browser")
                .contains("refreshDeliveryBlock()");
    }

    @Test
    void progressAndBulkButtonAreRenderedFromTheUnfilteredServerScope() {
        int start = sellerJs.indexOf("function deliveryProgressHtml(");
        int end = sellerJs.indexOf("async function refreshDeliveryBlock(");
        String block = sellerJs.substring(start, end);

        assertThat(block)
                .as("every figure comes from the server payload, never from the visible rows")
                .contains("p.activeOrderCount")
                .contains("p.deliveredCount")
                .contains("p.bulkScopeOrderCount");
        assertThat(block)
                .as("the bulk button quotes the server's scope, not the filtered subset")
                .contains("data-action=\"mark-all-delivered\"");
        assertThat(block)
                .as("the block carries the id the refresh replaces in place")
                .contains("id=\"deliveryBlock\"");

        int clickStart = sellerJs.indexOf("case 'mark-all-delivered': {");
        assertThat(clickStart).isGreaterThanOrEqualTo(0);
        String bulk = sellerJs.substring(clickStart, sellerJs.indexOf("case 'show-remark':", clickStart));
        assertThat(bulk)
                .as("the confirmation quotes a FRESH server count, so a stale tab cannot lie")
                .contains("offeringDetailUrl(bulkProductId)")
                .contains("fresh.deliveryProgress.bulkScopeOrderCount");
        assertThat(bulk)
                .as("the batch posts to the offering+date scoped endpoint")
                .contains("'/mark-all-delivered?date=' + sellerDate(S.selectedDate)")
                .contains("method: 'POST'");
        assertThat(bulk)
                .as("the toast reports what the server ACTUALLY changed, then re-reads the screen")
                .contains("res.appliedCount")
                .contains("await sellerRender()");
    }
}
