package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.dto.ProductDto;
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
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired SellerAppService sellerAppService;
    @Autowired AvailabilityService availabilityService;

    @Test
    @Transactional
    void cancellingPersistedOrderRestoresInventoryExactlyOnce() {
        Product product = offering(false);
        product.setMaxQuantity(1);
        product.setRemainingQuantity(0);
        product.setBookedQuantity(1);
        product.setAvailableToday(false);
        products.saveAndFlush(product);

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User buyer = users.saveAndFlush(new User("Buyer" + suffix, "93" + suffix + "0001", "A-1",
                UserRole.BUYER));
        Order placed = new Order(buyer, product.getKitchen());
        placed.setOrderNumber("INV-CANCEL-" + suffix);
        placed.setOrderStatus(OrderStatus.ORDERED);
        placed.setPaymentStatus(PaymentStatus.PENDING);
        placed.addItem(new OrderItem(product, 1, product.getPrice()));
        placed.recalculateTotal();
        placed = orders.saveAndFlush(placed);

        orderService.cancelOrder(placed.getId(), buyer);
        Order cancelled = orders.findById(placed.getId()).orElseThrow();
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.getBookedQuantity()).isZero();
        assertThat(restored.getAvailableToday()).isTrue();

        orderService.cancelOrder(placed.getId(), buyer);
        Product afterRetry = products.findById(product.getId()).orElseThrow();
        assertThat(afterRetry.getRemainingQuantity()).isEqualTo(1);
        assertThat(afterRetry.getBookedQuantity()).isZero();
    }

    @Test
    @Transactional
    void cancellingSoldOutUnpausedOfferingRestoresAvailability() {
        Product product = offering(false);
        assertThat(products.restoreStock(product.getId(), 1, AppTimeZone.today())).isEqualTo(1);
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.getBookedQuantity()).isEqualTo(1);
        assertThat(restored.getAvailableToday()).isTrue();
    }

    @Test
    @Transactional
    void cancellingPausedOfferingRestoresStockWithoutReopeningIt() {
        Product product = offering(true);
        assertThat(products.restoreStock(product.getId(), 1, AppTimeZone.today())).isEqualTo(1);
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.getBookedQuantity()).isEqualTo(1);
        assertThat(restored.getAvailableToday()).isFalse();
        assertThat(restored.isOrdersPaused()).isTrue();
    }

    @Test
    @Transactional
    void cancellationRestorationDoesNotOverrideManualSoldOut() {
        Product product = offering(false);
        product.setSoldOutManually(true);
        products.saveAndFlush(product);

        assertThat(products.restoreStock(product.getId(), 1, AppTimeZone.today())).isEqualTo(1);
        assertThat(products.reopenAfterRestore(product.getId(), AppTimeZone.today())).isZero();
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.isSoldOutManually()).isTrue();
        assertThat(restored.getAvailableToday()).isFalse();
    }

    @Test
    @Transactional
    void cancellingFuturePreorderRestoresStockWithoutOpeningToday() {
        Product product = offering(false);
        product.setAvailableDate(java.time.LocalDate.now().plusDays(1));
        product.setIsPreorder(true);
        product.setAvailableToday(false);
        products.saveAndFlush(product);

        assertThat(products.restoreStock(product.getId(), 1, AppTimeZone.today())).isEqualTo(1);
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.getAvailableToday()).isFalse();
        assertThat(restored.getIsPreorder()).isTrue();
    }

    @Test
    @Transactional
    void manualSoldOutIsTerminalForOccurrenceAndCannotBeReopened() {
        Product product = offering(false);
        product.setRemainingQuantity(2);
        product = products.saveAndFlush(product);
        User seller = product.getKitchen().getSeller();

        ProductDto soldOut = sellerAppService.markSoldOut(product.getId(), seller);
        assertThat(soldOut.getSoldOut()).isTrue();
        assertThat(soldOut.getSoldOutManually()).isTrue();
        assertThat(products.findById(product.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(2);

        ProductDto replenished = sellerAppService.updateInventory(product.getId(), 1, seller);
        assertThat(replenished.getSoldOut()).isTrue();
        assertThat(replenished.getSoldOutManually()).isTrue();
        assertThat(replenished.getAvailableToday()).isFalse();

        // Terminal policy: markAvailable and resumeOrders cannot reopen the same manually sold-out occurrence
        final Long pId = product.getId();
        final User sUser = seller;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sellerAppService.markAvailable(pId, sUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manually sold-out");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sellerAppService.resumeOrders(pId, sUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manually sold-out");

        // The occurrence remains strictly sold out
        Product current = products.findById(product.getId()).orElseThrow();
        assertThat(current.isSoldOutManually()).isTrue();
        assertThat(current.getAvailableToday()).isFalse();
    }

    @Test
    @Transactional
    void replenishingSoldOutPreorderRequiresExplicitReactivation() {
        Product product = offering(false);
        product.setIsPreorder(true);
        product.setAvailableDate(AppTimeZone.today().plusDays(1));
        product.setRemainingQuantity(0);
        product.setAvailableToday(false);
        product = products.saveAndFlush(product);
        User seller = product.getKitchen().getSeller();

        ProductDto replenished = sellerAppService.updateInventory(product.getId(), 1, seller);
        assertThat(replenished.getSoldOut()).isTrue();
        assertThat(replenished.getManualReactivationRequired()).isTrue();

        ProductDto available = sellerAppService.markAvailable(product.getId(), seller);
        assertThat(available.getSoldOut()).isFalse();
        assertThat(available.getManualReactivationRequired()).isFalse();
        assertThat(available.getAvailableToday()).isTrue();
        assertThat(available.getLifecycleState()).isEqualTo("PRE_ORDER");
    }

    @Test
    @Transactional
    void futurePreorderIsOrderableEvenWhenNotAvailableToday() {
        Product product = offering(false);
        product.getKitchen().setAvailableToday(true);
        kitchens.saveAndFlush(product.getKitchen());
        product.setIsPreorder(true);
        product.setAvailableDate(AppTimeZone.today().plusDays(1));
        product.setAvailableToday(false);
        product.setRemainingQuantity(2);
        product = products.saveAndFlush(product);

        assertThat(availabilityService.isPreorderable(product, null)).isTrue();
        assertThat(availabilityService.isAvailableToday(product, null)).isFalse();
    }


    @Test
    @Transactional
    void cancellingPastDayExpiredOfferingDoesNotReopenAvailability() {
        Product product = offering(false);
        product.setAvailableDate(AppTimeZone.today().minusDays(1));
        products.saveAndFlush(product);

        assertThat(products.restoreStock(product.getId(), 1, AppTimeZone.today())).isEqualTo(1);
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.getAvailableToday()).isFalse();
    }

    @Test
    @Transactional
    void cancellingOrderWhilePausedRestoresStockWithoutReopening() {
        Product product = offering(true);
        product.setAvailableDate(AppTimeZone.today());
        products.saveAndFlush(product);

        assertThat(products.restoreStock(product.getId(), 1, AppTimeZone.today())).isEqualTo(1);
        Product restored = products.findById(product.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(1);
        assertThat(restored.isOrdersPaused()).isTrue();
        assertThat(restored.getAvailableToday()).isFalse();
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
