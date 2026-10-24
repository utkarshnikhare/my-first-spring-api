package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.OrderItemRequest;
import com.example.my_first_spring_api.dto.PlaceOrderRequest;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 5: Genuine Bounded Load Simulation.
 * Exercises real end-to-end HTTP requests over loopback TCP sockets
 * against an embedded web server running HikariCP and H2.
 * Tiers evaluated: 1 VU, 10 VUs, 25 VUs, and 50 VUs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:bounded-load-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.datasource.hikari.maximum-pool-size=60",
        "spring.datasource.hikari.connection-timeout=30000"
})
@ActiveProfiles("test")
public class BoundedLoadSimulationTest {

    @LocalServerPort
    private int port;

    @Autowired private UserRepository users;
    @Autowired private AreaRepository areas;
    @Autowired private SocietyRepository societies;
    @Autowired private KitchenRepository kitchens;
    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private String baseUrl;

    private Long sharedKitchenId;
    private String sharedKitchenSlug;
    private Long sharedProductId;
    private Society sharedSociety;

    @BeforeEach
    void setupBenchmarkData() {
        baseUrl = "http://localhost:" + port;
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Area area = areas.saveAndFlush(new Area("Load Area " + suffix));
        sharedSociety = societies.saveAndFlush(new Society(area, "Load Society " + suffix));

        User seller = new User("Load Seller " + suffix, "98" + suffix + "01", "LS-1", UserRole.SELLER);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setAreaRef(area);
        seller.setSocietyRef(sharedSociety);
        seller = users.saveAndFlush(seller);

        Kitchen kitchen = new Kitchen("k-load-" + suffix, "Load Kitchen " + suffix, "Speciality", null, seller);
        kitchen.setSociety(sharedSociety.getName());
        kitchen.setAvailableToday(true);
        kitchen.getServedSocieties().add(sharedSociety);
        kitchen = kitchens.saveAndFlush(kitchen);
        sharedKitchenId = kitchen.getId();
        sharedKitchenSlug = kitchen.getSlug();

        Product product = new Product(kitchen, "Thali " + suffix, "Desc", BigDecimal.valueOf(180), null);
        product.setAvailableToday(true);
        product.setAvailableDate(LocalDate.now());
        product.setOrderWindowEnd("23:59");
        product.setReadyByTime(LocalDate.now().atTime(23, 59).toString());
        product.setRemainingQuantity(2000);
        product.setMaxQuantity(2000);
        product = products.saveAndFlush(product);
        sharedProductId = product.getId();
    }

    static class VuMetrics {
        final int vus;
        final int totalRequests;
        final int successRequests;
        final int failedRequests;
        final double throughputRps;
        final long minMs;
        final long medianMs;
        final long p95Ms;
        final long p99Ms;
        final long maxMs;

        VuMetrics(int vus, int totalRequests, int successRequests, int failedRequests,
                  double throughputRps, long minMs, long medianMs, long p95Ms, long p99Ms, long maxMs) {
            this.vus = vus;
            this.totalRequests = totalRequests;
            this.successRequests = successRequests;
            this.failedRequests = failedRequests;
            this.throughputRps = throughputRps;
            this.minMs = minMs;
            this.medianMs = medianMs;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
            this.maxMs = maxMs;
        }
    }

    static class VuContext {
        final HttpClient client;
        final String cookieHeader;
        final String xsrf;
        final String mobile;
        final int vuIdx;

        VuContext(HttpClient client, String cookieHeader, String xsrf, String mobile, int vuIdx) {
            this.client = client;
            this.cookieHeader = cookieHeader;
            this.xsrf = xsrf;
            this.mobile = mobile;
            this.vuIdx = vuIdx;
        }
    }

    private VuContext prepareVu(int vuIdx) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Map<String, String> cookies = new LinkedHashMap<>();
        HttpRequest prime = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/auth/me")).GET().build();
        HttpResponse<String> primeRes = client.send(prime, HttpResponse.BodyHandlers.ofString());
        for (String c : primeRes.headers().allValues("Set-Cookie")) {
            String pair = c.split(";")[0].trim();
            int eq = pair.indexOf('=');
            if (eq > 0) cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
        }

        String mobile = "9" + String.format("%09d", Math.abs(Long.hashCode(vuIdx * 10007L + System.nanoTime())) % 1_000_000_000);
        Map<String, Object> regPayload = Map.of(
                "name", "VU Buyer " + vuIdx,
                "mobileNumber", mobile,
                "password", "Password1234!",
                "flatHouseNumber", "VU-" + vuIdx,
                "building", "Wing A",
                "areaId", sharedSociety.getArea().getId(),
                "societyId", sharedSociety.getId()
        );

        String primeCookieHeader = cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));

        HttpRequest regReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/register/buyer"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", cookies.getOrDefault("XSRF-TOKEN", ""))
                .header("Cookie", primeCookieHeader)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(regPayload)))
                .build();

        HttpResponse<String> regRes = client.send(regReq, HttpResponse.BodyHandlers.ofString());
        if (regRes.statusCode() != 201) {
            throw new IllegalStateException("VU preparation failed: " + regRes.statusCode() + " " + regRes.body());
        }

        for (String c : regRes.headers().allValues("Set-Cookie")) {
            String pair = c.split(";")[0].trim();
            int eq = pair.indexOf('=');
            if (eq > 0) cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
        }

        String authedCookieHeader = cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));

        return new VuContext(client, authedCookieHeader, cookies.getOrDefault("XSRF-TOKEN", ""), mobile, vuIdx);
    }

    private VuMetrics runVuTier(int vuCount, int iterationsPerVu) throws Exception {
        List<VuContext> vuContexts = new ArrayList<>();
        for (int v = 0; v < vuCount; v++) {
            vuContexts.add(prepareVu(v));
        }

        ExecutorService pool = Executors.newFixedThreadPool(vuCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(vuCount);

        List<Long> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger totalReq = new AtomicInteger(0);
        AtomicInteger successReq = new AtomicInteger(0);
        AtomicInteger failReq = new AtomicInteger(0);

        for (VuContext ctx : vuContexts) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    HttpClient client = ctx.client;
                    String xsrf = ctx.xsrf;
                    String mobile = ctx.mobile;
                    int vuIdx = ctx.vuIdx;

                    for (int it = 0; it < iterationsPerVu; it++) {
                        // 1. Discovery API: GET /api/discovery/kitchens
                        long t0 = System.nanoTime();
                        String encodedSoc = URLEncoder.encode(sharedSociety.getName(), StandardCharsets.UTF_8);
                        HttpRequest discReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/api/discovery/kitchens?society=" + encodedSoc))
                                .header("Cookie", ctx.cookieHeader)
                                .GET()
                                .build();
                        HttpResponse<String> discRes = client.send(discReq, HttpResponse.BodyHandlers.ofString());
                        long d0 = (System.nanoTime() - t0) / 1_000_000;
                        latencies.add(d0);
                        totalReq.incrementAndGet();
                        if (discRes.statusCode() == 200) successReq.incrementAndGet(); else failReq.incrementAndGet();

                        // 2. Kitchen Offerings API: GET /api/kitchens/{slug}
                        long t1 = System.nanoTime();
                        HttpRequest kitReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/api/kitchens/" + sharedKitchenSlug))
                                .header("Cookie", ctx.cookieHeader)
                                .GET()
                                .build();
                        HttpResponse<String> kitRes = client.send(kitReq, HttpResponse.BodyHandlers.ofString());
                        long d1 = (System.nanoTime() - t1) / 1_000_000;
                        latencies.add(d1);
                        totalReq.incrementAndGet();
                        if (kitRes.statusCode() == 200) successReq.incrementAndGet(); else failReq.incrementAndGet();

                        // 3. Draft Cart API: POST /api/buyer/orders/draft
                        Thread.sleep((long) (Math.random() * 20));
                        long t2 = System.nanoTime();
                        String itemJson = mapper.writeValueAsString(List.of(Map.of("productId", sharedProductId, "quantity", 1)));
                        HttpRequest draftReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/api/buyer/orders/draft?kitchenId=" + sharedKitchenId))
                                .header("Content-Type", "application/json")
                                .header("X-XSRF-TOKEN", xsrf)
                                .header("Cookie", ctx.cookieHeader)
                                .POST(HttpRequest.BodyPublishers.ofString(itemJson))
                                .build();
                        HttpResponse<String> draftRes = client.send(draftReq, HttpResponse.BodyHandlers.ofString());
                        long d2 = (System.nanoTime() - t2) / 1_000_000;
                        latencies.add(d2);
                        totalReq.incrementAndGet();
                        if (draftRes.statusCode() == 200) {
                            successReq.incrementAndGet();
                        } else {
                            System.err.println("Draft failed: " + draftRes.statusCode() + " " + draftRes.body());
                            failReq.incrementAndGet();
                        }

                        // 4. Place Order API: POST /api/buyer/orders/place
                        Thread.sleep((long) (Math.random() * 20));
                        long t3 = System.nanoTime();
                        Map<String, Object> bd = Map.of(
                                "name", "VU Buyer " + vuIdx,
                                "mobileNumber", mobile,
                                "society", sharedSociety.getName(),
                                "building", "Wing A",
                                "flatHouseNumber", "VU-" + vuIdx
                        );
                        Map<String, Object> placePayload = Map.of(
                                "kitchenId", sharedKitchenId,
                                "items", List.of(Map.of("productId", sharedProductId, "quantity", 1)),
                                "buyerDetails", bd,
                                "customInstructions", "Load iteration " + it,
                                "paymentStatus", "PENDING"
                        );
                        HttpRequest placeReq = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/api/buyer/orders/place"))
                                .header("Content-Type", "application/json")
                                .header("X-XSRF-TOKEN", xsrf)
                                .header("Cookie", ctx.cookieHeader)
                                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(placePayload)))
                                .build();
                        HttpResponse<String> placeRes = client.send(placeReq, HttpResponse.BodyHandlers.ofString());
                        long d3 = (System.nanoTime() - t3) / 1_000_000;
                        latencies.add(d3);
                        totalReq.incrementAndGet();
                        if (placeRes.statusCode() == 200) {
                            successReq.incrementAndGet();
                        } else {
                            System.err.println("Place failed: " + placeRes.statusCode() + " " + placeRes.body());
                            failReq.incrementAndGet();
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    failReq.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        long wallStart = System.nanoTime();
        startGate.countDown();
        boolean finished = endGate.await(45, TimeUnit.SECONDS);
        long wallDurationMs = (System.nanoTime() - wallStart) / 1_000_000;
        pool.shutdown();

        assertThat(finished).as("Tier with %d VUs must complete within timeout", vuCount).isTrue();

        Collections.sort(latencies);
        int n = latencies.size();
        long min = n > 0 ? latencies.get(0) : 0;
        long max = n > 0 ? latencies.get(n - 1) : 0;
        long p50 = n > 0 ? latencies.get((int) (n * 0.50)) : 0;
        long p95 = n > 0 ? latencies.get((int) (n * 0.95)) : 0;
        long p99 = n > 0 ? latencies.get(Math.min(n - 1, (int) (n * 0.99))) : 0;
        double rps = wallDurationMs > 0 ? (totalReq.get() * 1000.0) / wallDurationMs : 0.0;

        return new VuMetrics(vuCount, totalReq.get(), successReq.get(), failReq.get(), rps, min, p50, p95, p99, max);
    }

    @Test
    @DisplayName("Bounded Load Test: 1 VU, 10 VUs, 25 VUs, 50 VUs realistic journey throughput and latency")
    void testBoundedApplicationLoadAcrossTiers() throws Exception {
        System.out.println("\n=========================================================================");
        System.out.println("SOCIOMART BOUNDED LOAD TEST REPORT — REAL HTTP END-TO-END WORKFLOWS");
        System.out.println("=========================================================================");

        VuMetrics tier1 = runVuTier(1, 10);
        VuMetrics tier10 = runVuTier(10, 3);
        VuMetrics tier25 = runVuTier(25, 2);
        VuMetrics tier50 = runVuTier(50, 1);

        List<VuMetrics> tiers = List.of(tier1, tier10, tier25, tier50);

        System.out.printf("%-8s | %-12s | %-10s | %-10s | %-12s | %-8s | %-8s | %-8s | %-8s\n",
                "VUs", "Total Reqs", "Success", "Failed", "Throughput", "p50(ms)", "p95(ms)", "p99(ms)", "Max(ms)");
        System.out.println("---------------------------------------------------------------------------------------------------------");
        for (VuMetrics m : tiers) {
            System.out.printf("%-8d | %-12d | %-10d | %-10d | %-10.1f/s | %-8d | %-8d | %-8d | %-8d\n",
                    m.vus, m.totalRequests, m.successRequests, m.failedRequests, m.throughputRps,
                    m.medianMs, m.p95Ms, m.p99Ms, m.maxMs);
            assertThat(m.failedRequests).as("No HTTP failures or connection exhaustion allowed at %d VUs", m.vus).isEqualTo(0);
        }
        System.out.println("=========================================================================\n");

        // Verify Data Integrity after all 4 tiers completed
        int expectedOrdersPlaced = (1 * 10) + (10 * 3) + (25 * 2) + (50 * 1); // 10 + 30 + 50 + 50 = 140 orders
        assertThat(orders.count())
                .as("All checkout orders must be durably stored")
                .isEqualTo(expectedOrdersPlaced);

        Product finalProduct = products.findById(sharedProductId).orElseThrow();
        assertThat(2000 - finalProduct.getRemainingQuantity())
                .as("Exact inventory decrement matches total completed checkout orders")
                .isEqualTo(expectedOrdersPlaced);
        assertThat(finalProduct.getBookedQuantity())
                .as("Booked quantity matches orders placed")
                .isEqualTo(expectedOrdersPlaced);
    }
}
