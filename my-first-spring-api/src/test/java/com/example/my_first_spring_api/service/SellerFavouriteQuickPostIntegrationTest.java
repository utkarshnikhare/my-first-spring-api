package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.dto.QuickPostDto;
import com.example.my_first_spring_api.dto.SellerTemplateDto;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:favourite-quickpost-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class SellerFavouriteQuickPostIntegrationTest {
    @Autowired SellerAppService sellerApp;
    @Autowired KitchenService kitchenService;
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired SellerTemplateRepository templates;
    @Autowired QuickPostRepository quickPosts;

    private User seller;
    private User otherSeller;
    private Kitchen kitchen;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User sellerEntity = new User("Seller" + suffix, "91" + suffix + "0001", null, UserRole.SELLER);
        sellerEntity.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.save(sellerEntity);
        User otherSellerEntity = new User("Other" + suffix, "92" + suffix + "0001", null, UserRole.SELLER);
        otherSellerEntity.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        otherSeller = users.save(otherSellerEntity);
        kitchen = kitchens.save(new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller));
        kitchen.setAvailableToday(true);
        kitchen = kitchens.save(kitchen);
    }

    @Test
    void sellerFavouritesAreCappedAtThreeAndRemovalFreesSpace() {
        sellerApp.addTemplate(seller, template("One"));
        sellerApp.addTemplate(seller, template("Two"));
        sellerApp.addTemplate(seller, template("Three"));
        assertThat(templates.findBySellerOrderByCreatedAtDesc(seller)).hasSize(3);

        assertThatThrownBy(() -> sellerApp.addTemplate(seller, template("Four")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum 3");

        List<SellerTemplateDto> current = sellerApp.getTemplates(seller);
        sellerApp.deleteTemplate(current.get(0).getId(), seller);
        sellerApp.addTemplate(seller, template("Replacement"));
        assertThat(templates.findBySellerOrderByCreatedAtDesc(seller)).hasSize(3);
        assertThat(templates.findBySellerOrderByCreatedAtDesc(otherSeller)).isEmpty();
    }

    @Test
    void favouriteTemplatesDoNotEnterProductHistory() {
        sellerApp.addTemplate(seller, template("Saved template"));
        Product yesterday = products.save(new Product(kitchen, "Published yesterday", "", BigDecimal.TEN, null));
        yesterday.setAvailableDate(LocalDate.now().minusDays(1));
        yesterday.setAvailableToday(false);
        yesterday.setIsPreorder(false);
        products.save(yesterday);
        Product today = products.save(new Product(kitchen, "Published today", "", BigDecimal.TEN, null));
        today.setAvailableDate(LocalDate.now());
        today.setAvailableToday(true);
        products.save(today);

        assertThat(sellerApp.getRecentOfferings(seller))
                .extracting("name").containsExactly("Published yesterday")
                .doesNotContain("Saved template", "Published today");
    }

    @Test
    void quickPostPersistsAsTodayAndIsVisibleWithoutCreatingProduct() {
        long productCountBefore = products.count();
        QuickPostDto result = sellerApp.createQuickPost(seller, "Fresh food today", null,
                "request-1", LocalDate.now());
        QuickPostDto duplicate = sellerApp.createQuickPost(seller, "Fresh food today", null,
                "request-1", LocalDate.now());

        assertThat(result.getPostedDate()).isEqualTo(LocalDate.now());
        assertThat(duplicate.getId()).isEqualTo(result.getId());
        assertThat(quickPosts.findByKitchenAndPostedDateOrderByCreatedAtDesc(kitchen, LocalDate.now()))
                .hasSize(1);
        assertThat(products.count()).isEqualTo(productCountBefore);
        assertThat(sellerApp.getQuickPosts(seller)).extracting("message").containsExactly("Fresh food today");
        KitchenDetailDto publicDetail = kitchenService.getKitchenDetailById(kitchen.getId(), null);
        assertThat(publicDetail.getQuickPosts()).extracting("message").containsExactly("Fresh food today");
    }

    @Test
    void quickPostAcceptsOptionalImageButRejectsBlankFutureAndForeignUse() {
        String image = "data:image/png;base64,aGVsbG8=";
        QuickPostDto withImage = sellerApp.createQuickPost(seller, "Photo announcement", image,
                "request-image", null);
        assertThat(withImage.getImageData()).isEqualTo(image);
        assertThatThrownBy(() -> sellerApp.createQuickPost(seller, "  ", null, "blank", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sellerApp.createQuickPost(seller, "Future", null, "future", LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Today only");
        assertThatThrownBy(() -> sellerApp.createQuickPost(otherSeller, "Wrong seller", null, "foreign", null))
                .isInstanceOf(RuntimeException.class);
    }

    private SellerTemplateDto template(String name) {
        SellerTemplateDto dto = new SellerTemplateDto();
        dto.setName(name);
        dto.setPrice(BigDecimal.valueOf(40));
        dto.setPriceUnit("Per Plate");
        dto.setMaxQuantity(5);
        dto.setCategory("LUNCH");
        return dto;
    }
}
