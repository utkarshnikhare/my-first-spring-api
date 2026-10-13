package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inventory-audit-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class InventoryRestorationIntegrationTest {
    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;

    @Test
    @Transactional
    void cancellingSoldOutUnpausedOfferingRestoresAvailability() {
        Product product = offering(false);
        assertThat(products.restoreStock(product.getId(), 1)).isEqualTo(1);
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.getBookedQuantity()).isEqualTo(1);
        assertThat(restored.getAvailableToday()).isTrue();
    }

    @Test
    @Transactional
    void cancellingPausedOfferingRestoresStockWithoutReopeningIt() {
        Product product = offering(true);
        assertThat(products.restoreStock(product.getId(), 1)).isEqualTo(1);
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.getBookedQuantity()).isEqualTo(1);
        assertThat(restored.getAvailableToday()).isFalse();
        assertThat(restored.isOrdersPaused()).isTrue();
    }

    @Test
    @Transactional
    void cancellingFuturePreorderRestoresStockWithoutOpeningToday() {
        Product product = offering(false);
        product.setAvailableDate(java.time.LocalDate.now().plusDays(1));
        product.setIsPreorder(true);
        product.setAvailableToday(false);
        products.saveAndFlush(product);

        assertThat(products.restoreStock(product.getId(), 1)).isEqualTo(1);
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.getAvailableToday()).isFalse();
        assertThat(restored.getIsPreorder()).isTrue();
    }


    private Product offering(boolean paused) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User seller = new User("Seller" + suffix, "91" + suffix + "0001", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.save(seller);
        Kitchen kitchen = new Kitchen("k" + suffix, "Kitchen", "", null, seller);
        kitchen = kitchens.save(kitchen);
        Product product = new Product(kitchen, "Poha", "", BigDecimal.valueOf(40), null);
        product.setAvailableToday(false);
        product.setMaxQuantity(3);
        product.setRemainingQuantity(0);
        product.setBookedQuantity(2);
        product.setOrdersPaused(paused);
        return products.saveAndFlush(product);
    }
}
