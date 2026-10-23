package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.DemoDataSeeder;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.SellerDashboardDto;
import com.example.my_first_spring_api.dto.SellerTemplateDto;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.SellerTemplateRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Favourites and History are screens over persisted rows - they are not separate
 * entities and they are not demo-only UI. Both were reported as "shows nothing",
 * and the endpoints proved it: a freshly seeded database contains no
 * SellerTemplate row and no offering whose offering date has already passed, so
 * both queries legitimately returned empty lists while the UI silently looked
 * like a broken page.
 *
 * These tests pin the seed that now creates that archive, and pin the rules the
 * archive must never break: the 3-favourite cap, template/History separation,
 * the dashboard's authoritative offering-date filter, and idempotent reseeding.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seller-archive-seed-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("demo")
class DemoSellerArchiveSeedTest {

    /** The mobile number the Seller App demo-login signs in as. */
    private static final String DEMO_SELLER_MOBILE = "9100000001";

    @Autowired DemoDataSeeder seeder;
    @Autowired SellerAppService sellerApp;
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired SellerTemplateRepository templates;
    @Autowired ProductRepository products;

    private User aarti;
    private Kitchen kitchen;

    @BeforeEach
    void seedDemoData() {
        seeder.seedAll();
        aarti = users.findByMobileNumber(DEMO_SELLER_MOBILE).orElseThrow();
        kitchen = kitchens.findBySeller(aarti).get(0);
    }

    @Test
    void favouritesScreenReadsPersistedSellerTemplates() {
        List<SellerTemplateDto> favourites = sellerApp.getTemplates(aarti);

        assertThat(favourites).as("demo seller must have saved favourites to display").isNotEmpty();
        assertThat(favourites).allMatch(t -> t.getName() != null && t.getPrice() != null);
        // The rows are real SellerTemplate records, so every existing template action
        // applies to them unchanged - including the cap the create form enforces.
        assertThat(templates.findBySellerOrderByCreatedAtDesc(aarti)).hasSize(favourites.size());
        assertThat(favourites).as("seed respects the live 3-favourite cap").hasSize(3);
        assertThatThrownBy(() -> sellerApp.addTemplate(aarti, newTemplate("One too many")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum 3");
    }

    @Test
    void historyScreenReadsOfferingsWhoseDateHasPassed() {
        List<ProductDto> history = sellerApp.getRecentOfferings(aarti);

        assertThat(history).as("demo kitchen must have past offerings to display").isNotEmpty();
        assertThat(history).allMatch(p -> p.getAvailableDate() != null
                && p.getAvailableDate().isBefore(LocalDate.now()));
        // Favourites are templates, never history; today's offerings stay live.
        assertThat(history).extracting("name").doesNotContain(favourites().toArray(String[]::new));
    }

    /**
     * A dish that is on sale today must not also be listed in History: the two
     * screens would show the same name with different dates and look broken. The
     * first archive seed reused live catalog names, so this rule is pinned here.
     */
    @Test
    void historyNeverRepeatsADishThatIsOnSaleToday() {
        List<String> live = sellerApp.getDashboard(aarti).getOfferings().stream()
                .map(ProductDto::getName).toList();
        List<String> archived = sellerApp.getRecentOfferings(aarti).stream()
                .map(ProductDto::getName).toList();

        assertThat(live).as("the kitchen must have live offerings to be a real conflict").isNotEmpty();
        assertThat(archived).as("the archive must not be empty either").isNotEmpty();
        assertThat(archived).as("archived dishes must be distinct from on-sale dishes")
                .doesNotContainAnyElementsOf(live);
    }

    @Test
    void archivedOfferingsStayOffTheDashboardAndAreNotMarkedAvailableToday() {
        sellerApp.getRecentOfferings(aarti);

        SellerDashboardDto dashboard = sellerApp.getDashboard(aarti);
        assertThat(dashboard.getOfferings()).isNotEmpty();
        assertThat(dashboard.getOfferings()).allMatch(p -> p.getAvailableDate() == null
                || !p.getAvailableDate().isBefore(LocalDate.now()));

        assertThat(products.findByKitchenAndAvailableDateBeforeOrderByAvailableDateDescCreatedAtDesc(kitchen, LocalDate.now()))
                .as("history rows are archived, not on sale")
                .allMatch(p -> !Boolean.TRUE.equals(p.getAvailableToday()));
    }

    @Test
    void reseedingNeverDuplicatesTheArchive() {
        int favouritesBefore = sellerApp.getTemplates(aarti).size();
        int historyBefore = sellerApp.getRecentOfferings(aarti).size();
        long productsBefore = products.count();

        seeder.seedAll();
        seeder.seedSellerArchiveIfEmpty();

        assertThat(sellerApp.getTemplates(aarti)).hasSize(favouritesBefore);
        assertThat(sellerApp.getRecentOfferings(aarti)).hasSize(historyBefore);
        assertThat(products.count()).isEqualTo(productsBefore);
    }

    private List<String> favourites() {
        return sellerApp.getTemplates(aarti).stream().map(SellerTemplateDto::getName).toList();
    }

    private SellerTemplateDto newTemplate(String name) {
        SellerTemplateDto dto = new SellerTemplateDto();
        dto.setName(name);
        dto.setPrice(BigDecimal.valueOf(40));
        dto.setPriceUnit("plate");
        dto.setMaxQuantity(5);
        dto.setCategory("LUNCH");
        return dto;
    }
}
