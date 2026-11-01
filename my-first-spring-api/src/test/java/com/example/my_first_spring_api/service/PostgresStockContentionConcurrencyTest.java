package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private Area area;
    private Society society;
    private Kitchen kitchen;

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
        productRepository.deleteAll();
        kitchenRepository.deleteAll();
        userRepository.deleteAll();
        societyRepository.deleteAll();
        areaRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        area = areaRepository.saveAndFlush(new Area("Postgres Area " + suffix));
        society = societyRepository.saveAndFlush(new Society(area, "Postgres Society " + suffix));

        User seller = new User("Postgres Seller", "910000" + suffix, "PS-1", UserRole.SELLER);
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
                            "paymentStatus", "PAID",
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
}
