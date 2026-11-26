package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:yesterday-offerings-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SellerYesterdayOfferingsIntegrationTest {

    @Autowired SellerAppService sellerApp;
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;

    private User sellerA;
    private Kitchen kitchenA;
    private User sellerB;
    private Kitchen kitchenB;

    @BeforeEach
    void setUp() {
        products.deleteAll();
        kitchens.deleteAll();
        users.deleteAll();

        String suffixA = UUID.randomUUID().toString().substring(0, 8);
        sellerA = new User("Seller A", "91" + suffixA + "01", "S-1", UserRole.SELLER);
        sellerA.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        sellerA = users.saveAndFlush(sellerA);

        kitchenA = new Kitchen("k-a-" + suffixA, "Kitchen A", "Desc", null, sellerA);
        kitchenA.setAvailableToday(true);
        kitchenA = kitchens.saveAndFlush(kitchenA);

        String suffixB = UUID.randomUUID().toString().substring(0, 8);
        sellerB = new User("Seller B", "91" + suffixB + "02", "S-1", UserRole.SELLER);
        sellerB.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        sellerB = users.saveAndFlush(sellerB);

        kitchenB = new Kitchen("k-b-" + suffixB, "Kitchen B", "Desc", null, sellerB);
        kitchenB.setAvailableToday(true);
        kitchenB = kitchens.saveAndFlush(kitchenB);
    }

    @Test
    @Transactional
    void getYesterdayOfferings_returnsOnlyYesterdayPublishedOfferingsForSeller() {
        LocalDate yesterday = AppTimeZone.today().minusDays(1);
        LocalDate today = AppTimeZone.today();
        LocalDate twoDaysAgo = yesterday.minusDays(1);

        // 1. Eligible yesterday offering for Seller A (Rice)
        Product rice = new Product(kitchenA, "Steamed Rice", "Fresh rice", BigDecimal.valueOf(120), null);
        rice.setAvailableDate(yesterday);
        rice.setAvailableToday(true);
        rice.setMaxQuantity(15);
        rice.setRemainingQuantity(0); // even if sold out yesterday, content is reusable
        rice.setOrderWindowStart("09:00");
        rice.setOrderWindowEnd("12:00");
        rice.setReadyByTime("13:00");
        rice.setArchived(false);
        rice.setPublicationDraft(false);
        rice = products.saveAndFlush(rice);

        // 2. Eligible yesterday offering for Seller A (Dal)
        Product dal = new Product(kitchenA, "Yellow Dal Fry", "Homestyle dal", BigDecimal.valueOf(140), null);
        dal.setAvailableDate(yesterday);
        dal.setAvailableToday(true);
        dal.setMaxQuantity(20);
        dal.setRemainingQuantity(5);
        dal.setOrderWindowStart("09:00");
        dal.setOrderWindowEnd("12:00");
        dal.setReadyByTime("13:00");
        dal.setArchived(false);
        dal.setPublicationDraft(false);
        dal = products.saveAndFlush(dal);

        // 3. Offering scheduled for Today (Must be excluded)
        Product todayItem = new Product(kitchenA, "Today Poha", "Breakfast", BigDecimal.valueOf(80), null);
        todayItem.setAvailableDate(today);
        todayItem.setAvailableToday(true);
        todayItem.setMaxQuantity(10);
        todayItem.setArchived(false);
        todayItem.setPublicationDraft(false);
        products.saveAndFlush(todayItem);

        // 4. Offering from 2 days ago (Must be excluded)
        Product oldItem = new Product(kitchenA, "Old Biryani", "Special", BigDecimal.valueOf(250), null);
        oldItem.setAvailableDate(twoDaysAgo);
        oldItem.setAvailableToday(true);
        oldItem.setMaxQuantity(10);
        oldItem.setArchived(false);
        oldItem.setPublicationDraft(false);
        products.saveAndFlush(oldItem);

        // 5. Unpublished draft from yesterday (Must be excluded)
        Product draftItem = new Product(kitchenA, "Unpublished Kheer", "Dessert", BigDecimal.valueOf(90), null);
        draftItem.setAvailableDate(yesterday);
        draftItem.setAvailableToday(false);
        draftItem.setMaxQuantity(10);
        draftItem.setArchived(false);
        draftItem.setPublicationDraft(true);
        products.saveAndFlush(draftItem);

        // 6. Archived item from yesterday (Must be excluded)
        Product archivedItem = new Product(kitchenA, "Deleted Salad", "Green", BigDecimal.valueOf(70), null);
        archivedItem.setAvailableDate(yesterday);
        archivedItem.setAvailableToday(false);
        archivedItem.setMaxQuantity(10);
        archivedItem.setArchived(true);
        archivedItem.setPublicationDraft(false);
        products.saveAndFlush(archivedItem);

        // 7. Yesterday offering belonging to Seller B (Must be excluded for Seller A)
        Product sellerBItem = new Product(kitchenB, "Seller B Pasta", "Italian", BigDecimal.valueOf(200), null);
        sellerBItem.setAvailableDate(yesterday);
        sellerBItem.setAvailableToday(true);
        sellerBItem.setMaxQuantity(10);
        sellerBItem.setArchived(false);
        sellerBItem.setPublicationDraft(false);
        products.saveAndFlush(sellerBItem);

        // Act
        List<ProductDto> resultA = sellerApp.getYesterdayOfferings(sellerA);
        List<ProductDto> resultB = sellerApp.getYesterdayOfferings(sellerB);

        // Assert
        assertThat(resultA).hasSize(2);
        assertThat(resultA).extracting(ProductDto::getName).containsExactlyInAnyOrder("Steamed Rice", "Yellow Dal Fry");
        assertThat(resultA).extracting(ProductDto::getAvailableDate).containsOnly(yesterday);

        // Verify seller isolation
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).getName()).isEqualTo("Seller B Pasta");
    }

    @Test
    @Transactional
    void getYesterdayOfferings_whenNoOfferingsYesterday_returnsEmptyList() {
        List<ProductDto> result = sellerApp.getYesterdayOfferings(sellerA);
        assertThat(result).isNotNull().isEmpty();
    }
}
