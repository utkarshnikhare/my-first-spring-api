package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Native PostgreSQL Stock Contention Concurrency Verification.
 *
 * <p>Exercises 20 concurrent buyer threads competing for 3 units of stock
 * over real PostgreSQL transactions (Flyway-managed schema + Hibernate ddl-auto=validate).
 * Verifies that PostgreSQL MVCC and pessimistic/optimistic locking semantics
 * prevent overselling under high concurrency.</p>
 *
 * <p>Skipped cleanly if Docker is not available in the local execution environment.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.flyway.enabled=true",
        "sociomart.auth.password-login-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=30"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresStockContentionConcurrencyTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
    }

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private SocietyRepository societyRepository;
    @Autowired private KitchenRepository kitchenRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private SellerAppService sellerAppService;
    @Autowired private SellerService sellerService;
    @Autowired private FavouriteService favouriteService;
    @Autowired private FavouriteRepository favouriteRepository;
    @Autowired private OrderService orderService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private Area area;
    private Society society;
    private Kitchen kitchen;
    private User seller;

    private MockHttpSession createSession(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BuyerService.BUYER_SESSION_KEY, user.getId());
        String role = user.getRole().name();
        SecurityContext sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);
        return session;
    }

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        favouriteRepository.deleteAll();
        productRepository.deleteAll();
        kitchenRepository.deleteAll();
        userRepository.deleteAll();
        societyRepository.deleteAll();
        areaRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        area = areaRepository.saveAndFlush(new Area("Postgres Area " + suffix));
        society = societyRepository.saveAndFlush(new Society(area, "Postgres Society " + suffix));

        seller = new User("Postgres Seller", "910000" + suffix, "PS-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setAreaRef(area);
        seller.setSocietyRef(society);
        seller = userRepository.saveAndFlush(seller);

        kitchen = new Kitchen("k-pg-" + suffix, "PG Kitchen " + suffix, "North Indian", null, seller);
        kitchen.setSociety(society.getName());
        kitchen.setAcceptedPaymentMode(SellerPaymentMode.GPAY_AND_UPI);
        kitchen.setSellerType(SellerType.KITCHEN);
        kitchen.setAvailableToday(true);
        kitchen.getServedSocieties().add(society);
        kitchen = kitchenRepository.saveAndFlush(kitchen);
    }

    private User createBuyer(String name) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User buyer = new User(name, "97" + suffix, "Flat " + suffix, UserRole.BUYER);
        buyer.setAreaRef(area);
        buyer.setSocietyRef(society);
        buyer.setSociety(society.getName());
        buyer.setBuilding("PG Tower");
        buyer.setFlatHouseNumber("Flat " + suffix);
        return userRepository.saveAndFlush(buyer);
    }

    private Product createProduct(Kitchen k, String name, BigDecimal price, int stock) {
        Product product = new Product(k, name, "Delicious", price, null);
        product.setAvailableToday(true);
        product.setAvailableDate(LocalDate.now());
        product.setRemainingQuantity(stock);
        product.setMaxQuantity(stock);
        product.setCutoffTime("23:59");
        product.setOrderWindowEnd("23:59");
        product.setOrdersPaused(false);
        return productRepository.saveAndFlush(product);
    }

    @Test
    @DisplayName("PG-ADV-01: Native PostgreSQL 20-buyer stock contention competing for 3 units never oversells")
    void nativePostgres20BuyerStockContention() throws Exception {
        Product scarceItem = createProduct(kitchen, "Limited Special Thali", BigDecimal.valueOf(250), 3);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<MockHttpSession> sessions = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            User buyer = createBuyer("PgBuyer_" + i);
            MockHttpSession session = createSession(buyer);

            List<Map<String, Object>> draftItems = List.of(Map.of("productId", scarceItem.getId(), "quantity", 1));
            mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchen.getId())
                            .session(session)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(draftItems)))
                    .andExpect(status().isOk());

            sessions.add(session);
        }

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            final MockHttpSession session = sessions.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    if (index > 0) Thread.sleep(index * 15L); // Stagger to simulate realistic multi-user arrival

                    Map<String, Object> placePayload = Map.of(
                            "kitchenId", kitchen.getId(),
                            "items", List.of(Map.of("productId", scarceItem.getId(), "quantity", 1)),
                            "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                            "paymentMethod", "upi"
                    );

                    MvcResult res = mvc.perform(post("/api/buyer/orders/place")
                                    .session(session)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsBytes(placePayload)))
                            .andReturn();

                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 3 orders must succeed
        assertThat(successCount.get()).isEqualTo(3);
        assertThat(failCount.get()).isEqualTo(17);

        // Product inventory in PostgreSQL must be strictly 0, never negative
        Product updatedProduct = productRepository.findById(scarceItem.getId()).orElseThrow();
        assertThat(updatedProduct.getRemainingQuantity()).isEqualTo(0);

        // Exactly 3 non-draft orders recorded in PostgreSQL
        long nonDraftOrders = orderRepository.findByKitchenOrderByCreatedAtDesc(kitchen).stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT)
                .count();
        assertThat(nonDraftOrders).isEqualTo(3);
    }

    @Test
    @DisplayName("PostgreSQL checkout rejects a stale draft after the seller pauses the offering")
    void staleDraftCannotPlaceAfterOfferingIsPaused() throws Exception {
        Product item = createProduct(kitchen, "Pause-sensitive offering", BigDecimal.valueOf(40), 3);
        User buyer = createBuyer("StaleDraftBuyer");
        MockHttpSession session = createSession(buyer);

        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchen.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(List.of(
                                Map.of("productId", item.getId(), "quantity", 1)))))
                .andExpect(status().isOk());

        sellerAppService.pauseOrders(item.getId(), seller);

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchen.getId(),
                "items", List.of(Map.of("productId", item.getId(), "quantity", 1)),
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi"
        );
        mvc.perform(post("/api/buyer/orders/place")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isBadRequest());

        assertThat(productRepository.findById(item.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("PostgreSQL checkout rejects a stale draft after the seller marks it sold out")
    void staleDraftCannotPlaceAfterOfferingIsMarkedSoldOut() throws Exception {
        Product item = createProduct(kitchen, "Manual sold-out offering", BigDecimal.valueOf(40), 3);
        User buyer = createBuyer("SoldOutDraftBuyer");
        MockHttpSession session = createSession(buyer);

        mvc.perform(post("/api/buyer/orders/draft?kitchenId=" + kitchen.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(List.of(
                                Map.of("productId", item.getId(), "quantity", 1)))))
                .andExpect(status().isOk());

        sellerAppService.markSoldOut(item.getId(), seller);

        Map<String, Object> placePayload = Map.of(
                "kitchenId", kitchen.getId(),
                "items", List.of(Map.of("productId", item.getId(), "quantity", 1)),
                "paymentStatus", "AWAITING_SELLER_VERIFICATION",
                "paymentMethod", "upi"
        );
        mvc.perform(post("/api/buyer/orders/place")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(placePayload)))
                .andExpect(status().isBadRequest());

        assertThat(productRepository.findById(item.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("PostgreSQL rating a stale product cannot undo a committed archive")
    void ratingStaleProductDoesNotUndoArchive() throws Exception {
        Product item = createProduct(kitchen, "Archive rating offering", BigDecimal.valueOf(40), 3);
        User buyer = createBuyer("ArchiveRatingBuyer");
        MockHttpSession session = createSession(buyer);
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(item.getId());
        request.setQuantity(1);
        Long orderId = orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request), session).getId();
        orderService.placeOrder(PaymentStatus.PENDING, "upi", null, null, session);
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(order);
        });
        CountDownLatch staleLoaded = new CountDownLatch(1);
        CountDownLatch allowRating = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> rating = executor.submit(() -> transactionTemplate.execute(status -> {
                Product stale = productRepository.findById(item.getId()).orElseThrow();
                if (stale.isArchived()) throw new IllegalStateException("Product was already archived");
                staleLoaded.countDown();
                try {
                    if (!allowRating.await(8, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Archive did not commit");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Rating transaction interrupted", ex);
                }
                return orderService.rateOrder(orderId, 5, buyer);
            }));
            assertThat(staleLoaded.await(8, TimeUnit.SECONDS)).isTrue();
            sellerService.deleteProduct(item.getId(), seller);
            allowRating.countDown();
            rating.get(10, TimeUnit.SECONDS);
            Product saved = productRepository.findById(item.getId()).orElseThrow();
            assertThat(saved.isArchived()).isTrue();
            assertThat(saved.getRating()).isEqualTo(5.0);
            assertThat(orderRepository.findById(orderId).orElseThrow().getRating()).isEqualTo(5);
        } finally {
            allowRating.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("PostgreSQL checkout waits for archive and rejects the stale draft")
    void checkoutRevalidatesAfterConcurrentArchiveCommits() throws Exception {
        Product item = createProduct(kitchen, "Archive lock offering", BigDecimal.valueOf(40), 3);
        User buyer = createBuyer("ArchiveLockBuyer");
        MockHttpSession session = createSession(buyer);
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(item.getId());
        request.setQuantity(1);
        Long orderId = orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request), session).getId();
        String applicationName = "archive-checkout-" + UUID.randomUUID();
        CountDownLatch archived = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        AtomicInteger archivePid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> archive = executor.submit(() -> transactionTemplate.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '8s'");
                archivePid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                sellerService.deleteProduct(item.getId(), seller);
                archived.countDown();
                try {
                    if (!allowCommit.await(8, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Checkout did not reach the product lock");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Archive transaction interrupted", ex);
                }
                return null;
            }));
            assertThat(archived.await(8, TimeUnit.SECONDS)).isTrue();
            Future<?> checkout = executor.submit(() -> transactionTemplate.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '8s'");
                jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class, applicationName);
                PlaceOrderRequest.BuyerDetails details = new PlaceOrderRequest.BuyerDetails();
                details.setName("Updated draft lock buyer");
                return orderService.placeOrder(PaymentStatus.PENDING, "upi", details, null, session);
            }));
            assertThat(awaitBlockedBy(applicationName, archivePid.get(), "products"))
                    .as("checkout must wait for the archive's product row lock").isTrue();
            allowCommit.countDown();
            archive.get(10, TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> checkout.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            Product saved = productRepository.findById(item.getId()).orElseThrow();
            assertThat(saved.isArchived()).isTrue();
            assertThat(saved.getRemainingQuantity()).isEqualTo(3);
            assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.DRAFT);
        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("PostgreSQL checkout and favourite use buyer-before-kitchen locking")
    void checkoutAndFavouriteDoNotDeadlockOnBuyerAndKitchen() throws Exception {
        Product item = createProduct(kitchen, "Favourite lock offering", BigDecimal.valueOf(40), 3);
        User buyer = createBuyer("FavouriteLockBuyer");
        MockHttpSession session = createSession(buyer);
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(item.getId());
        request.setQuantity(1);
        Long orderId = orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request), session).getId();
        String applicationName = "checkout-lock-" + UUID.randomUUID();
        CountDownLatch buyerLocked = new CountDownLatch(1);
        CountDownLatch allowFavouriteInsert = new CountDownLatch(1);
        AtomicInteger favouritePid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> favourite = executor.submit(() -> transactionTemplate.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '8s'");
                favouritePid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                User lockedBuyer = userRepository.findByIdForUpdate(buyer.getId()).orElseThrow();
                buyerLocked.countDown();
                try {
                    if (!allowFavouriteInsert.await(8, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Checkout did not reach the buyer lock");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Favourite transaction interrupted", ex);
                }
                return favouriteService.toggleKitchen(lockedBuyer, kitchen.getId());
            }));
            assertThat(buyerLocked.await(8, TimeUnit.SECONDS)).isTrue();
            Future<?> checkout = executor.submit(() -> transactionTemplate.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '8s'");
                jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class, applicationName);
                PlaceOrderRequest.BuyerDetails details = new PlaceOrderRequest.BuyerDetails();
                details.setName("Updated checkout buyer");
                return orderService.placeOrder(PaymentStatus.PENDING, "upi", details, null, session);
            }));

            assertThat(awaitBlockedBy(applicationName, favouritePid.get()))
                    .as("checkout must wait for the favourite's buyer lock").isTrue();
            allowFavouriteInsert.countDown();
            assertThat(favourite.get(10, TimeUnit.SECONDS)).isTrue();
            checkout.get(10, TimeUnit.SECONDS);
            assertThat(favouriteRepository.findByUserIdAndKitchenId(buyer.getId(), kitchen.getId())).isPresent();
            assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(productRepository.findById(item.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(2);
            assertThat(userRepository.findById(buyer.getId()).orElseThrow().getName()).isEqualTo("Updated draft lock buyer");
            assertThat(userRepository.findById(buyer.getId()).orElseThrow().getName()).isEqualTo("Updated checkout buyer");
        } finally {
            allowFavouriteInsert.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("PostgreSQL checkout and new draft use buyer-before-product locking")
    void checkoutAndDraftInsertDoNotDeadlockOnBuyerAndProduct() throws Exception {
        Product item = createProduct(kitchen, "Draft lock offering", BigDecimal.valueOf(40), 3);
        User buyer = createBuyer("DraftLockBuyer");
        MockHttpSession session = createSession(buyer);
        OrderItemRequest request = new OrderItemRequest();
        request.setProductId(item.getId());
        request.setQuantity(1);
        Long checkoutOrderId = orderService.createOrUpdateDraftOrder(kitchen.getId(), List.of(request), session).getId();
        String applicationName = "draft-lock-" + UUID.randomUUID();
        CountDownLatch draftInserted = new CountDownLatch(1);
        CountDownLatch allowItemInsert = new CountDownLatch(1);
        AtomicInteger draftPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> draft = executor.submit(() -> transactionTemplate.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '8s'");
                draftPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                User managedBuyer = userRepository.findById(buyer.getId()).orElseThrow();
                Kitchen managedKitchen = kitchenRepository.findById(kitchen.getId()).orElseThrow();
                Order newDraft = orderRepository.saveAndFlush(new Order(managedBuyer, managedKitchen));
                draftInserted.countDown();
                try {
                    if (!allowItemInsert.await(8, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Checkout did not reach the draft's buyer lock");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Draft transaction interrupted", ex);
                }
                Product managedProduct = productRepository.findById(item.getId()).orElseThrow();
                OrderItem draftItem = new OrderItem(managedProduct, 1, managedProduct.getPrice());
                draftItem.setOrder(newDraft);
                orderItemRepository.saveAndFlush(draftItem);
                return newDraft.getId();
            }));
            assertThat(draftInserted.await(8, TimeUnit.SECONDS)).isTrue();
            Future<?> checkout = executor.submit(() -> transactionTemplate.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '8s'");
                jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class, applicationName);
                PlaceOrderRequest.BuyerDetails details = new PlaceOrderRequest.BuyerDetails();
                details.setName("Updated draft lock buyer");
                return orderService.placeOrder(PaymentStatus.PENDING, "upi", details, null, session);
            }));
            assertThat(awaitBlockedBy(applicationName, draftPid.get()))
                    .as("checkout must wait for the draft's buyer foreign-key lock").isTrue();
            allowItemInsert.countDown();
            Long draftId = draft.get(10, TimeUnit.SECONDS);
            checkout.get(10, TimeUnit.SECONDS);
            assertThat(orderRepository.findById(draftId).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.DRAFT);
            assertThat(orderRepository.findById(checkoutOrderId).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(productRepository.findById(item.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(2);
            assertThat(userRepository.findById(buyer.getId()).orElseThrow().getName()).isEqualTo("Updated draft lock buyer");
        } finally {
            allowItemInsert.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean awaitBlockedBy(String applicationName, int blockingPid) throws InterruptedException {
        return awaitBlockedBy(applicationName, blockingPid, "users");
    }

    private boolean awaitBlockedBy(String applicationName, int blockingPid, String table) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE application_name = ? "
                            + "AND ? = ANY(pg_blocking_pids(pid)) "
                            + "AND query ~* ?",
                    Integer.class, applicationName, blockingPid,
                    "(^|[^a-z0-9_])" + table + "([^a-z0-9_]|$)");
            if (waiting != null && waiting > 0) return true;
            Thread.sleep(25);
        }
        return false;
    }
}
