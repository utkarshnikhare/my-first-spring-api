package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrency-load-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.datasource.hikari.maximum-pool-size=60",
        "spring.datasource.hikari.connection-timeout=30000"
})
@ActiveProfiles("test")
public class ConcurrencyLoadSimulationTest {

    @Autowired UserRepository users;
    @Autowired AreaRepository areas;
    @Autowired SocietyRepository societies;
    @Autowired KitchenRepository kitchens;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired TransactionTemplate txTemplate;

    @Test
    @DisplayName("Adversarial Concurrency: 25 simultaneous buyers ordering the single last remaining stock unit")
    void testConcurrentOrderPlacementOnLastUnitStock() throws Exception {
        runConcurrentBuyersOnSingleStock(25);
    }

    @Test
    @DisplayName("Adversarial Concurrency: 50 simultaneous buyers ordering the single last remaining stock unit (Target Pilot Load)")
    void test50ConcurrentBuyersStockIntegrity() throws Exception {
        runConcurrentBuyersOnSingleStock(50);
    }

    private void runConcurrentBuyersOnSingleStock(int buyerCount) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Area area = areas.saveAndFlush(new Area("Area " + suffix));
        Society society = societies.saveAndFlush(new Society(area, "Society " + suffix));

        User seller = new User("Seller" + suffix, "91" + suffix + "01", "S-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setAreaRef(area);
        seller.setSocietyRef(society);
        seller = users.saveAndFlush(seller);

        Kitchen kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchen.setSociety(society.getName());
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.CASH_ONLY);
        kitchen.setAvailableToday(true);
        kitchen.getServedSocieties().add(society);
        kitchen = kitchens.saveAndFlush(kitchen);

        // Product with EXACTLY 1 unit remaining
        Product product = new Product(kitchen, "Limited Dish " + suffix, "desc", BigDecimal.valueOf(150), null);
        product.setAvailableToday(true);
        product.setAvailableDate(LocalDate.now());
        product.setCutoffTime("23:59");
        product.setOrderWindowEnd("23:59");
        product.setReadyByTime(LocalDate.now().atTime(23, 59).toString());
        product.setRemainingQuantity(1);
        product.setMaxQuantity(1);
        product = products.saveAndFlush(product);

        final Long productId = product.getId();
        final Long kitchenId = kitchen.getId();

        List<User> buyerList = new ArrayList<>();
        List<MockHttpSession> sessionList = new ArrayList<>();

        for (int i = 0; i < buyerCount; i++) {
            User buyer = new User("Buyer" + suffix + "_" + i, "92" + suffix + String.format("%03d", i), "Flat-" + i, UserRole.BUYER);
            buyer.setAreaRef(area);
            buyer.setSocietyRef(society);
            buyer.setSociety(society.getName());
            buyer.setBuilding("Tower A");
            buyer = users.saveAndFlush(buyer);
            buyerList.add(buyer);

            MockHttpSession session = new MockHttpSession();
            session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());

            OrderItemRequest req = new OrderItemRequest();
            req.setProductId(productId);
            req.setQuantity(1);

            txTemplate.execute(status -> {
                orderService.createOrUpdateDraftOrder(kitchenId, List.of(req), session);
                return null;
            });

            sessionList.add(session);
        }

        ExecutorService executor = Executors.newFixedThreadPool(buyerCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(buyerCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> failureReasons = new CopyOnWriteArrayList<>();

        for (int i = 0; i < buyerCount; i++) {
            final int idx = i;
            final User b = buyerList.get(idx);
            final MockHttpSession s = sessionList.get(idx);

            executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize all threads to fire at the exact same millisecond
                    PlaceOrderRequest.BuyerDetails bd = new PlaceOrderRequest.BuyerDetails();
                    bd.setName(b.getName());
                    bd.setMobileNumber(b.getMobileNumber());
                    bd.setSociety(society.getName());
                    bd.setSocietyId(society.getId());
                    bd.setBuilding(b.getBuilding());
                    bd.setFlatHouseNumber(b.getFlatHouseNumber());

                    OrderDto placed = orderService.placeOrder(PaymentStatus.PENDING, "cod", bd, "concurrency test " + buyerCount, s);
                    if (placed != null && placed.getOrderStatus() == OrderStatus.ORDERED) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                    failureReasons.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // Fire all threads simultaneously
        boolean completed = endGate.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get())
                .as("Exactly ONE buyer must successfully acquire the single remaining unit under %d concurrent threads", buyerCount)
                .isEqualTo(1);
        assertThat(failureCount.get())
                .as("The other %d buyers must be rejected due to stock exhaustion", buyerCount - 1)
                .isEqualTo(buyerCount - 1);

        Product reloadedProduct = products.findById(productId).orElseThrow();
        assertThat(reloadedProduct.getRemainingQuantity())
                .as("Stock must reach 0 and NEVER become negative under %d concurrent threads", buyerCount)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("Adversarial Concurrency: Same buyer clicking place order concurrently 10 times")
    void testConcurrentDuplicatePlacementBySameBuyer() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Area area = areas.saveAndFlush(new Area("Area " + suffix));
        final Society society = societies.saveAndFlush(new Society(area, "Society " + suffix));

        User sellerEntity = new User("Seller" + suffix, "91" + suffix + "02", "S-2", UserRole.SELLER);
        sellerEntity.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        User seller = users.saveAndFlush(sellerEntity);

        Kitchen kitchenEntity = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchenEntity.setSociety(society.getName());
        kitchenEntity.setAcceptedPaymentMode(SellerPaymentMode.CASH_ONLY);
        kitchenEntity.setAvailableToday(true);
        kitchenEntity.getServedSocieties().add(society);
        Kitchen kitchen = kitchens.saveAndFlush(kitchenEntity);

        Product productEntity = new Product(kitchen, "Meal " + suffix, "desc", BigDecimal.valueOf(200), null);
        productEntity.setAvailableToday(true);
        productEntity.setAvailableDate(LocalDate.now());
        productEntity.setOrderWindowEnd("23:59");
        productEntity.setReadyByTime(LocalDate.now().atTime(23, 59).toString());
        productEntity.setRemainingQuantity(20);
        productEntity.setMaxQuantity(20);
        Product product = products.saveAndFlush(productEntity);

        User buyerEntity = new User("Buyer" + suffix, "93" + suffix + "99", "B-101", UserRole.BUYER);
        buyerEntity.setAreaRef(area);
        buyerEntity.setSocietyRef(society);
        buyerEntity.setSociety(society.getName());
        buyerEntity.setBuilding("Tower B");
        final User buyer = users.saveAndFlush(buyerEntity);

        MockHttpSession sharedSession = new MockHttpSession();
        sharedSession.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(product.getId());
        req.setQuantity(2);

        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(req), sharedSession);

        int attempts = 10;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(attempts);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    PlaceOrderRequest.BuyerDetails bd = new PlaceOrderRequest.BuyerDetails();
                    bd.setName(buyer.getName());
                    bd.setMobileNumber(buyer.getMobileNumber());
                    bd.setSociety(society.getName());
                    bd.setBuilding(buyer.getBuilding());
                    bd.setFlatHouseNumber(buyer.getFlatHouseNumber());

                    OrderDto placed = orderService.placeOrder(PaymentStatus.PENDING, "cod", bd, "duplicate test", sharedSession);
                    if (placed != null) successCount.incrementAndGet();
                } catch (Exception ex) {
                    rejectedCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get())
                .as("Only one order placement can succeed from the single draft order")
                .isEqualTo(1);
        assertThat(rejectedCount.get()).isEqualTo(9);

        Product updatedProduct = products.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getRemainingQuantity())
                .as("Stock should be decremented by exactly 2 units, remaining 18")
                .isEqualTo(18);
    }

    @Test
    @DisplayName("Adversarial Concurrency: 10 concurrent requests to cancel the same order; stock restored exactly once")
    void testConcurrentCancellationStockRestorationIntegrity() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Area area = areas.saveAndFlush(new Area("Area " + suffix));
        Society society = societies.saveAndFlush(new Society(area, "Society " + suffix));

        User seller = new User("Seller" + suffix, "91" + suffix + "03", "S-3", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller = users.saveAndFlush(seller);

        Kitchen kitchen = new Kitchen("k" + suffix, "Kitchen " + suffix, "", null, seller);
        kitchen.setSociety(society.getName());
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.CASH_ONLY);
        kitchen.setAvailableToday(true);
        kitchen.getServedSocieties().add(society);
        kitchen = kitchens.saveAndFlush(kitchen);

        Product product = new Product(kitchen, "Biryani " + suffix, "desc", BigDecimal.valueOf(300), null);
        product.setAvailableToday(true);
        product.setAvailableDate(LocalDate.now());
        product.setOrderWindowEnd("23:59");
        product.setReadyByTime(LocalDate.now().atTime(23, 59).toString());
        product.setRemainingQuantity(10);
        product.setMaxQuantity(10);
        product = products.saveAndFlush(product);

        User buyerEntity = new User("Buyer" + suffix, "93" + suffix + "55", "C-303", UserRole.BUYER);
        buyerEntity.setAreaRef(area);
        buyerEntity.setSocietyRef(society);
        buyerEntity.setSociety(society.getName());
        buyerEntity.setBuilding("Tower C");
        final User buyer = users.saveAndFlush(buyerEntity);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, buyer.getId());

        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(product.getId());
        req.setQuantity(3);

        orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(req), session);

        PlaceOrderRequest.BuyerDetails bd = new PlaceOrderRequest.BuyerDetails();
        bd.setName(buyer.getName());
        bd.setMobileNumber(buyer.getMobileNumber());
        bd.setSociety(society.getName());
        bd.setBuilding(buyer.getBuilding());
        bd.setFlatHouseNumber(buyer.getFlatHouseNumber());

        final OrderDto placedOrder = orderService.placeOrder(PaymentStatus.PENDING, "cod", bd, "cancel test", session);
        assertThat(placedOrder).isNotNull();

        // Stock was 10, consumed 3 -> remaining is 7
        Product afterPlacement = products.findById(product.getId()).orElseThrow();
        assertThat(afterPlacement.getRemainingQuantity()).isEqualTo(7);

        // Now fire 10 concurrent cancel requests for placedOrder.getId()
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    orderService.cancelOrder(placedOrder.getId(), buyer);
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Product afterCancel = products.findById(product.getId()).orElseThrow();
        assertThat(afterCancel.getRemainingQuantity())
                .as("Stock must be restored by 3 units (from 7 to 10) and NEVER restored multiple times")
                .isEqualTo(10);

        Order cancelledOrder = orders.findById(placedOrder.getId()).orElseThrow();
        assertThat(cancelledOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
