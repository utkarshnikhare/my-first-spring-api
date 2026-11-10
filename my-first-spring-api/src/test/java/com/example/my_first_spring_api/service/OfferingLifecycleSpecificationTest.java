package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.SellerDashboardDto;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Authoritative integration test suite covering the Offering Lifecycle:
 * Pause, Sold Out, Expiry and Cancellation Restoration rules
 * from SocioMart_Pause_Sold_Out_Offering_Lifecycle_Developer_AI_Spec.docx.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:offering-lifecycle-spec-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class OfferingLifecycleSpecificationTest {

    @Autowired UserRepository users;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired SellerAppService sellerAppService;
    @Autowired KitchenService kitchenService;
    @Autowired MarketplaceService marketplaceService;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    @Transactional
    void pauseSetsStateToPausedAndRetainsInSellerDashboard() {
        Product p = createLiveProduct(5);
        User seller = p.getKitchen().getSeller();

        ProductDto paused = sellerAppService.pauseOrders(p.getId(), seller);
        assertThat(paused.getOrdersPaused()).isTrue();
        assertThat(paused.getLifecycleState()).isEqualTo("PAUSED");

        // Retained in seller dashboard
        SellerDashboardDto dash = sellerAppService.getDashboard(seller);
        assertThat(dash.getOfferings()).extracting(ProductDto::getId).contains(p.getId());
        ProductDto dashProduct = dash.getOfferings().stream()
                .filter(dto -> dto.getId().equals(p.getId()))
                .findFirst().orElseThrow();
        assertThat(dashProduct.getOrdersPaused()).isTrue();
        assertThat(dashProduct.getLifecycleState()).isEqualTo("PAUSED");
    }

    @Test
    @Transactional
    void pausedOfferingHiddenFromActiveBuyerDiscoveryAndCounts() {
        Product p = createLiveProduct(5);
        User seller = p.getKitchen().getSeller();
        User buyer = createBuyer();

        sellerAppService.pauseOrders(p.getId(), seller);

        // Hidden from marketplace available items
        List<ProductDto> items = marketplaceService.getAllAvailableItems(buyer);
        assertThat(items).extracting(ProductDto::getId).doesNotContain(p.getId());

        // Hidden from active search
        var searchRes = kitchenService.search(p.getName(), buyer);
        assertThat(searchRes.getProducts()).extracting(ProductDto::getId).doesNotContain(p.getId());

        // Direct link / API retrieves offering with PAUSED state
        ProductDto direct = kitchenService.getProductById(p.getId(), buyer);
        assertThat(direct.getOrdersPaused()).isTrue();
        assertThat(direct.getLifecycleState()).isEqualTo("PAUSED");
    }

    @Test
    @Transactional
    void pausedOfferingCannotBeOrderedAndReportsTemporarilyUnavailable() {
        Product p = createLiveProduct(5);
        User seller = p.getKitchen().getSeller();
        User buyer = createBuyer();

        sellerAppService.pauseOrders(p.getId(), seller);

        MockHttpSession session = createBuyerSession(buyer);
        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(p.getId());
        req.setQuantity(1);

        assertThatThrownBy(() -> orderService.createOrUpdateDraftOrder(p.getKitchen().getId(),
                Collections.singletonList(req), session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temporarily unavailable (orders paused)");
    }

    @Test
    @Transactional
    void resumeReturnsToLiveBeforeCutoffIfStockExists() {
        Product p = createLiveProduct(5);
        User seller = p.getKitchen().getSeller();

        sellerAppService.pauseOrders(p.getId(), seller);
        ProductDto resumed = sellerAppService.resumeOrders(p.getId(), seller);

        assertThat(resumed.getOrdersPaused()).isFalse();
        assertThat(resumed.getLifecycleState()).isEqualTo("LIVE");
    }

    @Test
    @Transactional
    void resumeRejectedIfExpired() {
        Product p = createLiveProduct(5);
        p.setAvailableDate(AppTimeZone.today().minusDays(1));
        p.setOrdersPaused(true);
        p = products.saveAndFlush(p);
        User seller = p.getKitchen().getSeller();

        Long pid = p.getId();
        assertThatThrownBy(() -> sellerAppService.resumeOrders(pid, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @Transactional
    void manualSoldOutSetsSoldOutManuallyAndTerminatesOccurrence() {
        Product p = createLiveProduct(5);
        User seller = p.getKitchen().getSeller();
        User buyer = createBuyer();

        ProductDto soldOut = sellerAppService.markSoldOut(p.getId(), seller);
        assertThat(soldOut.getSoldOut()).isTrue();
        assertThat(soldOut.getSoldOutManually()).isTrue();
        assertThat(soldOut.getSoldOutReason()).isEqualTo("MANUAL");
        assertThat(soldOut.getAvailableToday()).isFalse();

        // Resume must fail
        Long pid = p.getId();
        assertThatThrownBy(() -> sellerAppService.resumeOrders(pid, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manually sold-out offering cannot be reopened");

        // Mark available must fail
        assertThatThrownBy(() -> sellerAppService.markAvailable(pid, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manually sold-out offering cannot be reopened");

        // Direct fetch reflects MANUAL sold out
        ProductDto direct = kitchenService.getProductById(p.getId(), buyer);
        assertThat(direct.getSoldOut()).isTrue();
        assertThat(direct.getSoldOutReason()).isEqualTo("MANUAL");
    }

    @Test
    @Transactional
    void manualSoldOutNeverReopensAfterCancellation() {
        Product p = createLiveProduct(5);
        User seller = p.getKitchen().getSeller();
        User buyer = createBuyer();

        // Create an order first
        Order order = new Order(buyer, p.getKitchen());
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setOrderNumber("SM-SPEC-" + System.nanoTime());
        order.addItem(new OrderItem(p, 2, p.getPrice()));
        order.recalculateTotal();
        order = orders.saveAndFlush(order);

        // Now seller manually marks sold out
        sellerAppService.markSoldOut(p.getId(), seller);

        // Order is cancelled
        orderService.cancelOrder(order.getId(), buyer);

        Product refreshed = products.findById(p.getId()).orElseThrow();
        assertThat(refreshed.isSoldOutManually()).isTrue();
        assertThat(refreshed.getAvailableToday()).isFalse();
        assertThat(refreshed.getSoldOutReason()).isEqualTo("MANUAL");
    }

    @Test
    @Transactional
    void autoStockExhaustedSetsSoldOutReasonAndReopensOnCancellation() {
        Product p = createLiveProduct(2);
        p.setOrderWindowStart("00:00");
        p.setOrderWindowEnd("23:59");
        p.setAvailableToday(true);
        p = products.saveAndFlush(p);
        User buyer = createBuyer();

        Order order = new Order(buyer, p.getKitchen());
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setOrderNumber("SPEC-CANCEL-" + System.nanoTime());
        order.addItem(new OrderItem(p, 2, p.getPrice()));
        order.recalculateTotal();
        order = orders.saveAndFlush(order);

        // Consume remaining 2 units
        products.consumeStock(p.getId(), 2);

        Product exhausted = products.findById(p.getId()).orElseThrow();
        assertThat(exhausted.getRemainingQuantity()).isEqualTo(0);
        assertThat(exhausted.isSoldOut()).isTrue();
        assertThat(exhausted.isSoldOutManually()).isFalse();
        assertThat(exhausted.getSoldOutReason()).isEqualTo("AUTO_STOCK_EXHAUSTED");

        // Cancel the order within the open order window
        orderService.cancelOrder(order.getId(), buyer);

        Product reopened = products.findById(p.getId()).orElseThrow();
        assertThat(reopened.getRemainingQuantity()).isEqualTo(2);
        assertThat(reopened.isSoldOut()).isFalse();
        assertThat(reopened.getAvailableToday()).isTrue();
    }

    @Test
    @Transactional
    void cancellationWhilePausedRestoresStockWithoutReopening() {
        Product p = createLiveProduct(2);
        p.setOrderWindowStart("00:00");
        p.setOrderWindowEnd("23:59");
        p = products.saveAndFlush(p);
        User seller = p.getKitchen().getSeller();
        User buyer = createBuyer();

        Order order = new Order(buyer, p.getKitchen());
        order.setOrderStatus(OrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setOrderNumber("SPEC-PAUSE-" + System.nanoTime());
        order.addItem(new OrderItem(p, 1, p.getPrice()));
        order.recalculateTotal();
        order = orders.saveAndFlush(order);

        // Consume 1 unit
        products.consumeStock(p.getId(), 1);

        // Seller pauses orders
        sellerAppService.pauseOrders(p.getId(), seller);

        // Cancel order
        orderService.cancelOrder(order.getId(), buyer);

        Product restored = products.findById(p.getId()).orElseThrow();
        assertThat(restored.getRemainingQuantity()).isEqualTo(2);
        assertThat(restored.isOrdersPaused()).isTrue();
        assertThat(restored.getAvailableToday()).isFalse();
    }

    @Test
    @Transactional
    void noLimitOfferingsDoNotAutoSoldOut() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User seller = users.saveAndFlush(new User("Seller" + suffix, "91" + suffix + "0001", "S-1", UserRole.SELLER));
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.saveAndFlush(seller);
        Kitchen kitchen = kitchens.saveAndFlush(new Kitchen("k" + suffix, "Kitchen", "", null, seller));
        kitchen.setAvailableToday(true);
        kitchen = kitchens.saveAndFlush(kitchen);

        Product p = new Product(kitchen, "Unlimited Curry", "", BigDecimal.valueOf(50), null);
        p.setAvailableToday(true);
        p.setAvailableDate(AppTimeZone.today());
        p.setOrderWindowStart("00:00");
        p.setOrderWindowEnd("23:59");
        p.setMaxQuantity(null);
        p.setRemainingQuantity(null);
        p = products.saveAndFlush(p);

        assertThat(p.isSoldOut()).isFalse();
        assertThat(p.getSoldOutReason()).isNull();
    }

    @Test
    @Transactional
    void databaseReloadPreservesPersistentSoldOutStateAcrossPersistenceContextClear() {
        Product manualP = createLiveProduct(5);
        User seller = manualP.getKitchen().getSeller();
        sellerAppService.markSoldOut(manualP.getId(), seller);

        Product autoP = new Product(manualP.getKitchen(), "Auto Stock Dish", "Tasty", BigDecimal.valueOf(100), null);
        autoP.setAvailableToday(true);
        autoP.setAvailableDate(AppTimeZone.today());
        autoP.setOrderWindowStart("00:00");
        autoP.setOrderWindowEnd("23:59");
        autoP.setReadyByTime("23:59");
        autoP.setMaxQuantity(5);
        autoP.setRemainingQuantity(0);
        autoP.setBookedQuantity(5);
        autoP = products.saveAndFlush(autoP);

        // Force DB flush and clear persistence context (simulating fresh DB read/restart)
        entityManager.flush();
        entityManager.clear();

        Product reloadedManual = products.findById(manualP.getId()).orElseThrow();
        assertThat(reloadedManual.isSoldOut()).isTrue();
        assertThat(reloadedManual.isSoldOutManually()).isTrue();
        assertThat(reloadedManual.getSoldOutReason()).isEqualTo("MANUAL");

        Product reloadedAuto = products.findById(autoP.getId()).orElseThrow();
        assertThat(reloadedAuto.isSoldOut()).isTrue();
        assertThat(reloadedAuto.isSoldOutManually()).isFalse();
        assertThat(reloadedAuto.getSoldOutReason()).isEqualTo("AUTO_STOCK_EXHAUSTED");

        // DTO reconstruction reflects persistent database fields
        final Long manualPid = manualP.getId();
        final Long autoPid = autoP.getId();
        com.example.my_first_spring_api.dto.SellerDashboardDto dash = sellerAppService.getDashboard(seller);
        ProductDto sellerManualDto = dash.getOfferings().stream()
                .filter(dto -> dto.getId().equals(manualPid)).findFirst().orElseThrow();
        assertThat(sellerManualDto.getSoldOutReason()).isEqualTo("MANUAL");
        assertThat(sellerManualDto.getSoldOutManually()).isTrue();

        ProductDto sellerAutoDto = dash.getOfferings().stream()
                .filter(dto -> dto.getId().equals(autoPid)).findFirst().orElseThrow();
        assertThat(sellerAutoDto.getSoldOutReason()).isEqualTo("AUTO_STOCK_EXHAUSTED");
        assertThat(sellerAutoDto.getSoldOutManually()).isFalse();
    }

    @Test
    @Transactional
    void preorderCancellationAutomaticallyRestoresStockAndReopens() {
        Product preorderP = createLiveProduct(1);
        preorderP.setIsPreorder(true);
        preorderP.setAvailableToday(false);
        preorderP.setAvailableDate(AppTimeZone.today().plusDays(2));
        preorderP = products.saveAndFlush(preorderP);

        User buyer = createBuyer();
        Order order = new Order(buyer, preorderP.getKitchen());
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setOrderNumber("PREORDER-RESTORE-" + System.nanoTime());
        order.addItem(new OrderItem(preorderP, 1, preorderP.getPrice()));
        order.recalculateTotal();
        order = orders.saveAndFlush(order);

        // Consume 1 unit to exhaust stock
        products.consumeStock(preorderP.getId(), 1);

        Product exhaustedPreorder = products.findById(preorderP.getId()).orElseThrow();
        assertThat(exhaustedPreorder.getRemainingQuantity()).isEqualTo(0);
        assertThat(exhaustedPreorder.isSoldOut()).isTrue();
        assertThat(exhaustedPreorder.getSoldOutReason()).isEqualTo("AUTO_STOCK_EXHAUSTED");

        entityManager.flush();
        entityManager.clear();

        // Cancel preorder order
        orderService.cancelOrder(order.getId(), buyer);

        entityManager.flush();
        entityManager.clear();

        Product reopened = products.findById(preorderP.getId()).orElseThrow();
        assertThat(reopened.getRemainingQuantity()).isEqualTo(1);
        assertThat(reopened.isSoldOut()).isFalse();
        assertThat(reopened.getSoldOutReason()).isNull();

        // Verifying buyer sees PRE_ORDER section and can order
        ProductDto dto = kitchenService.getProductById(preorderP.getId(), buyer);
        assertThat(dto.getBuyerSection()).isEqualTo("PRE_ORDER");
        assertThat(dto.getSoldOut()).isFalse();
        assertThat(dto.getLifecycleState()).isEqualTo("PRE_ORDER");
    }

    @Test
    @Transactional
    void manualSoldOutTerminalBehaviourRejectsResumeAndMarkAvailable() {
        Product p = createLiveProduct(5);
        User seller = p.getKitchen().getSeller();
        sellerAppService.markSoldOut(p.getId(), seller);

        entityManager.flush();
        entityManager.clear();

        Long pid = p.getId();
        assertThatThrownBy(() -> sellerAppService.markAvailable(pid, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A manually sold-out offering cannot be reopened");

        assertThatThrownBy(() -> sellerAppService.resumeOrders(pid, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A manually sold-out offering cannot be reopened");
    }

    private Product createLiveProduct(int stock) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User seller = new User("Seller" + suffix, "91" + suffix + "0001", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.saveAndFlush(seller);
        Kitchen kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchen.setAvailableToday(true);
        kitchen = kitchens.saveAndFlush(kitchen);

        Product product = new Product(kitchen, "Dish " + suffix, "Tasty", BigDecimal.valueOf(100), null);
        product.setAvailableToday(true);
        product.setAvailableDate(AppTimeZone.today());
        product.setOrderWindowStart("00:00");
        product.setOrderWindowEnd("23:59");
        product.setReadyByTime("23:59");
        product.setMaxQuantity(stock);
        product.setRemainingQuantity(stock);
        product.setBookedQuantity(0);
        return products.saveAndFlush(product);
    }

    private User createBuyer() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return users.saveAndFlush(new User("Buyer" + suffix, "93" + suffix + "0001", "S-1", UserRole.BUYER));
    }

    private MockHttpSession createBuyerSession(User buyer) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", buyer);
        session.setAttribute("userId", buyer.getId());
        session.setAttribute("BUYER_USER", buyer.getId());
        return session;
    }
}
