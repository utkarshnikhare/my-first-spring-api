package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression cover for the Buyer/Seller UI update.
 *
 * <ul>
 *   <li>Kitchen ratings must not appear anywhere in the Buyer UI, while the backend
 *       fields stay intact for compatibility.</li>
 *   <li>Buyers must never see booked/sold counts; only the scarcity hint is shown,
 *       and only for a remaining quantity of 1..5.</li>
 *   <li>Buyer pre-order wording is "Order by" / "Delivery by", not "Cut-off".</li>
 *   <li>The Seller nav says "My Offerings" and the page reuses the existing
 *       Add Offering route with the history section underneath.</li>
 *   <li>The buyer community is chosen from the authoritative society directory and
 *       validated on the backend.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ui-update-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("demo")
class BuyerSellerUiUpdateTest {

    @Autowired BuyerService buyerService;
    @Autowired com.example.my_first_spring_api.DemoDataSeeder seeder;
    @Autowired com.example.my_first_spring_api.repository.UserRepository users;

    private static String buyerJs;
    private static String sellerJs;
    private static String sellerHtml;
    private static String discoveryDtos;

    @BeforeAll
    static void loadResources() throws IOException {
        Path js = Paths.get("src", "main", "resources", "static", "js");
        Path html = Paths.get("src", "main", "resources", "static");
        Path dto = Paths.get("src", "main", "java", "com", "example", "my_first_spring_api", "dto");
        buyerJs = read(js.resolve("buyer.js"));
        sellerJs = read(js.resolve("seller.js"));
        sellerHtml = read(html.resolve("seller.html"));
        discoveryDtos = read(dto.resolve("DiscoveryDtos.java"));
    }

    private static String read(Path p) throws IOException {
        assertThat(p).as("resource must exist: %s", p).exists();
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** A session signed in as the seeded demo buyer. */
    private MockHttpSession buyerSession() {
        seeder.seedAll();
        com.example.my_first_spring_api.model.User buyer =
                users.findByRole(com.example.my_first_spring_api.model.UserRole.BUYER).get(0);
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());
        return s;
    }

    // ---------- 1. Kitchen ratings removed from the Buyer UI ----------

    @Test
    void kitchenRatingsAreNotRenderedAnywhereInTheBuyerUi() {
        assertThat(buyerJs).as("kitchen card and detail must not render a rating")
                .doesNotContain("k.rating")
                .doesNotContain("home kitchen rating")
                .doesNotContain("pill-gold")
                .doesNotContain("★");
    }

    @Test
    void ratingDataIsPreservedInTheBackendForCompatibility() {
        // UI-only removal: the DTO field stays so stored ratings and existing API
        // consumers keep working.
        assertThat(discoveryDtos)
                .as("buyer KitchenCard keeps its rating field for API compatibility")
                .contains("private Double rating;");
    }

    // ---------- 2. Remaining-quantity display ----------

    @Test
    void buyersNeverSeeBookedOrSoldCounts() {
        assertThat(buyerJs).as("the booked/max progress bar must be gone")
                .doesNotContain("demand-bar")
                .doesNotContain("demand-label")
                .doesNotContain("booked");
    }

    @Test
    void scarcityHintUsesTheAuthoritativeRemainingQuantity() {
        int start = buyerJs.indexOf("function buyerScarcityLabel(");
        assertThat(start).as("buyerScarcityLabel must exist").isGreaterThanOrEqualTo(0);
        String fn = buyerJs.substring(start, buyerJs.indexOf("function offeringCardHtml("));
        assertThat(fn).as("reads the authoritative remaining quantity")
                .contains("p.remainingQuantity");
        assertThat(fn).as("null remaining = unlimited, never sold out and never a number")
                .contains("=== null")
                .contains("return ''");
        assertThat(fn).as("scarcity only up to 5")
                .contains("remaining > 5")
                .contains("Only ' + remaining + ' left");
    }

    @Test
    void soldOutStillUsesTheExistingAuthoritativeRule() {
        int start = buyerJs.indexOf("function offeringCardHtml(");
        String card = buyerJs.substring(start, start + 2500);
        assertThat(card).contains("p.remainingQuantity != null && p.remainingQuantity <= 0");
        assertThat(card).contains("Sold out");
    }

    // ---------- 3. "Cut-off" -> "Order by" / "Delivery by" ----------

    @Test
    void buyerUiSaysOrderByAndDeliveryByInsteadOfCutoff() {
        assertThat(buyerJs).as("no Buyer-facing cutoff wording remains")
                .doesNotContain("Order cutoff")
                .doesNotContain(", cutoff ");
        assertThat(buyerJs).as("pre-order card shows both labels")
                .contains("Order by:")
                .contains("Delivery by:");
    }

    @Test
    void orderByAndDeliveryByReadThePersistedSellerConfiguredTimes() {
        String card = buyerJs.substring(buyerJs.indexOf("function offeringCardHtml("),
                buyerJs.indexOf("function offeringCardHtml(") + 2500);
        // Same persisted fields the seller configured; only the wording changed.
        assertThat(card).as("order-closing time from the seller's cutoff field")
                .contains("p.cutoffTime");
        assertThat(card).as("delivery time from the seller's ready-by field")
                .contains("p.readyByTime");
        assertThat(card).as("dates from the persisted offering date")
                .contains("p.availableDate");
    }

    // ---------- 7. History -> My Offerings ----------

    @Test
    void sellerNavigationIsLabelledMyOfferings() {
        assertThat(sellerHtml).contains(">My Offerings<");
        assertThat(sellerHtml).as("the bottom-nav item is no longer labelled History")
                .doesNotContain(">History<");
        assertThat(sellerHtml).as("nav points at the new route")
                .contains("#/my-offerings");
    }

    @Test
    void oldHistoryRouteIsKeptWorkingAsAnAlias() {
        assertThat(sellerJs).as("existing bookmarks keep working")
                .contains("'#/history': sellerHistoryView");
        assertThat(sellerJs).contains("'#/my-offerings': sellerHistoryView");
        assertThat(sellerJs).as("both routes light up the same nav item")
                .contains("hash === '#/history' || hash === '#/my-offerings'");
    }

    @Test
    void myOfferingsPageReusesTheExistingAddOfferingFlow() {
        int start = sellerJs.indexOf("async function sellerHistoryView(");
        assertThat(start).isGreaterThanOrEqualTo(0);
        String view = sellerJs.substring(start, sellerJs.indexOf("// SCREEN 4: QUICK POST"));
        assertThat(view).contains("My Offerings");
        assertThat(view).as("primary action reuses the existing #/add route, no second form")
                .contains("+ Add Offering")
                .contains("href=\"#/add\"");
        assertThat(view).as("history section retained underneath")
                .contains("Offering History")
                .contains("history-card")
                .contains("republish-history");
        assertThat(view.indexOf("+ Add Offering")).as("Add Offering must precede the cards")
                .isLessThan(view.indexOf("history-card"));
    }

    @Test
    void historyEmptyAndErrorStatesAreUnchanged() {
        int start = sellerJs.indexOf("async function sellerHistoryView(");
        String view = sellerJs.substring(start, sellerJs.indexOf("// SCREEN 4: QUICK POST"));
        assertThat(view).contains("No previous items").contains("Could not load history");
    }

    // ---------- 4. Buyer community from the authoritative directory ----------

    @Test
    void buyerProfileUsesASocietyDropdownNotFreeText() {
        String view = buyerJs.substring(buyerJs.indexOf("async function profileView("),
                buyerJs.indexOf("async function profileView(") + 4000);
        assertThat(view).as("community is chosen from the authoritative list")
                .contains("/api/buyer/profile/societies")
                .contains("<select")
                .contains("name=\"society\"");
        assertThat(view).as("no typed community field remains")
                .doesNotContain("placeholder=\"e.g. Sunshine Society\"");
    }

    @Test
    void selectableSocietiesComeFromTheAuthoritativeDirectory() {
        List<String> societies = buyerService.getSelectableSocieties(buyerSession());
        assertThat(societies).as("the seeded communities must be offered").isNotEmpty();
        assertThat(societies).doesNotHaveDuplicates();
    }

    @Test
    void backendRejectsASocietyThatIsNotInTheDirectory() {
        MockHttpSession s = buyerSession();
        var dto = new com.example.my_first_spring_api.dto.BuyerProfileDto();
        dto.setSociety("Totally Made Up Community");
        assertThatThrownBy(() -> buyerService.updateProfile(dto, s))
                .as("free text must not be stored - it would break service-area eligibility")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anExistingBuyerProfileStaysValidAndEditable() {
        MockHttpSession s = buyerSession();
        String current = buyerService.getProfile(s).getSociety();
        assertThat(current).as("the seeded buyer has a society").isNotBlank();

        // Saving without touching the community must keep it unchanged.
        var unchanged = new com.example.my_first_spring_api.dto.BuyerProfileDto();
        unchanged.setSociety(current);
        assertThat(buyerService.updateProfile(unchanged, s).getSociety()).isEqualTo(current);

        // And the current value is always offered, even if it left the directory.
        assertThat(buyerService.getSelectableSocieties(s)).contains(current);
    }
}

