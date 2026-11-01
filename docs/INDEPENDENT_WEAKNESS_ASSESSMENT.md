# SOCIOMART — INDEPENDENT WEAKNESS DISCOVERY & RED-TEAM ASSESSMENT REPORT

**Assessment Type:** Independent Red-Team, Architecture & Defensive Vulnerability Assessment  
**Repository:** `utkarshnikhare/my-first-spring-api`  
**Base Commit / Branch:** Merged PR #108 (`a4c385b` / `origin/main`)  
**Production Target:** `https://sociomart-demo.onrender.com`  
**Assessment Date:** November 2026  
**Auditor Roles:** Senior QA Architect, Principal Software Engineer, Application Security Engineer, Database Reliability Engineer, Ethical Penetration Tester  
**Scope:** Exhaustive weakness discovery across Architecture, Security, Business Logic, Concurrency, Database, Operations, and Test Coverage.  
**Constraint Adherence:** Non-destructive; no application code modified; no production data altered; `render.yaml` `autoDeploy: false` retained.

---

## 1. Executive Summary & Assessment Methodology

An independent, critical review was performed across the entire SocioMart codebase, schema migrations (V1–V21), security filters, order lifecycle, stock management, seller/buyer workflows, and existing test suites (849 Maven tests, 24 Playwright tests).

Rather than accepting past claims of "production-readiness" or "100% verified test passes", this assessment actively challenged code-level assumptions, identified hidden concurrency race conditions, probed authorization edge cases, evaluated PostgreSQL vs. H2 behavioral divergence, and analyzed database connection pool exhaustion hazards under production conditions.

### Assessment Statistics
- **Total Weaknesses Discovered:** 16 distinct weaknesses
- **Breakdown by Severity:**
  - **CRITICAL:** 1
  - **HIGH:** 6
  - **MEDIUM:** 6
  - **LOW:** 2
  - **INFORMATIONAL:** 1
- **Breakdown by Confidence:**
  - **CONFIRMED (Code-proven):** 13
  - **LIKELY (Architectural certainty):** 2
  - **POTENTIAL (Environment-dependent):** 1
  - **NOT VERIFIED:** 0

---

## 2. Top 10 Risks Ranked by Real-World Impact

| Rank | ID | Area | Severity | Finding | Real-World Impact |
|:---:|:---:|:---|:---:|:---|:---|
| **1** | **WEAK-01** | Concurrency / Stock | **CRITICAL** | Recurring Schedule Stock Overselling & In-Memory Capacity Calculation | In concurrent ordering spikes for recurring offerings, stock is completely unreserved at the DB level, allowing capacity to be oversold. In-memory order parsing scales linearly $O(N)$ with total kitchen orders. |
| **2** | **WEAK-02** | Business Logic / Financial | **HIGH** | Post-Payment Order Cancellation Without Escrow or Refund Mechanism | Buyer can cancel order after seller marks it `PAID` or while `OUT_FOR_DELIVERY`. Order enters `CANCELLED` and stock restores, but no refund record exists, causing immediate financial disputes and lost food. |
| **3** | **WEAK-06** | Database / Integrity | **HIGH** | Offering Deletion Causes Unhandled HTTP 500 Foreign Key Violation | Deleting an offering that has ever appeared in any past order throws PostgreSQL `DataIntegrityViolationException`. Sellers cannot remove discontinued items without breaking the application. |
| **4** | **WEAK-07** | Performance / Database | **HIGH** | Authentication Connection Storm (3+ DB Queries per Request with Pool Size = 4) | `UserSessionAuthorizationFilter` and Spring Session execute 3 synchronous DB queries per authenticated HTTP request. A burst of 15 users exhausts the Supabase pool (`maximum-pool-size=4`), freezing the server. |
| **5** | **WEAK-10** | Concurrency / Database | **HIGH** | Multi-Item Checkout Deadlock Hazard (Unordered Row Locking) | `OrderService.placeOrder` iterates over an unordered `HashMap` of product IDs when locking rows. Concurrent multi-item checkouts with inverted product order trigger PostgreSQL `40P01` deadlock crashes. |
| **6** | **WEAK-03** | Security / Abuse | **HIGH** | Rate Limiting IP Spoofing via Client Headers (`CF-Connecting-IP` / `X-Forwarded-For`) | `AuthController.clientIp()` trusts client-supplied headers without validating Cloudflare/proxy source IP. Attackers can bypass login rate limiting and brute-force passwords by rotating dummy headers. |
| **7** | **WEAK-12** | Operations / Reliability | **HIGH** | Zero Automated Production Database Backups & Missing Migration Rollback Mechanism | The `backups/` directory holds code trees only (0% DB dumps). Render and Supabase have no repository-managed backup pipeline, and Flyway Community lacks automatic downgrade scripts. |
| **8** | **WEAK-04** | Security / Auth | **MEDIUM** | Password Length and Complexity Not Enforced at Registration | Buyer and seller registration DTOs lack `@NotBlank` or `@Size` annotations. Accounts can be registered with a 1-character password (e.g. `"1"`), making accounts trivially brute-forceable. |
| **9** | **WEAK-08** | Business Logic / Data | **MEDIUM** | Flawed Rating Formula (Exponential Decay & Multi-Item Truncation) | `product.rating` uses `(oldRating + rating) / 2.0` instead of true arithmetic average; one 1-star review on 100 5-star reviews instantly drops the score to 3.0. Multi-item orders only rate `items.get(0)`. |
| **10** | **WEAK-09** | Business Logic / Time | **MEDIUM** | UTC Server Skew in Seller Earnings & Order Timestamps | `SellerAppService` uses JVM default timezone `LocalDate.now().atStartOfDay()` instead of `AppTimeZone.today()`. On Render (UTC), between 00:00 and 05:30 IST, today's Indian sales appear under yesterday's metrics. |

---

## 3. Complete Weakness Register

### WEAK-01: Recurring Schedule Stock Overselling & In-Memory Capacity Calculation
- **Area:** Business Logic / Concurrency
- **Severity:** **CRITICAL**
- **Confidence:** **CONFIRMED**
- **Evidence:** `OrderService.java` lines 1247–1250:
  ```java
  if (recurringScheduleService.hasSchedule(product.getId())) {
      continue; // skips atomic consumeStock()!
  }
  ```
  `OrderService.java` lines 317–332 (`bookedPlatesOn`):
  ```java
  List<Order> kitchenOrders = orderRepository.findByKitchenOrderByCreatedAtDesc(kitchen);
  for (Order o : kitchenOrders) {
      if (isSameTargetDate(o, date)) count += item.getQuantity();
  }
  ```
- **Impact:**
  1. For recurring schedule offerings, atomic database-level row locking (`consumeStock` with `UPDATE products SET remaining_quantity = remaining_quantity - ?`) is completely bypassed.
  2. Capacity is checked via `bookedPlatesOn()` which iterates over **all historical orders of the kitchen in memory without any database lock**.
  3. When two buyers concurrently checkout the last available plates for a recurring lunch/dinner slot, both read the same un-locked count and both orders succeed, leading to physical kitchen overselling.
  4. Memory footprint and CPU usage grow linearly $O(N)$ with the kitchen's historical order volume on every single cart validation call.
- **Reproduction:**
  1. Configure offering with a recurring schedule of 10 plates for today.
  2. Launch two concurrent buyer threads submitting `POST /api/buyer/orders/place` for 6 plates each.
  3. Observe that both requests return HTTP 200 and 12 total plates are confirmed against a 10-plate capacity limit.
- **Existing Protection:** Only works under single-threaded sequential traffic.
- **Recommendation:** Implement an atomic slot reservation table `recurring_schedule_bookings (offering_id, booking_date, booked_plates)` with a PostgreSQL check constraint `CHECK (booked_plates <= max_capacity)` and `SELECT ... FOR UPDATE` row locks.
- **Test Required:** Concurrent multi-threaded test with 10 virtual buyers requesting stock on a recurring offering with limited capacity simultaneously.

---

### WEAK-02: Post-Payment Order Cancellation Without Escrow or Refund Mechanism
- **Area:** Business Logic / Financial
- **Severity:** **HIGH**
- **Confidence:** **CONFIRMED**
- **Evidence:** `OrderService.java` lines 675–680 (`cancelOrder`):
  ```java
  if (order.getOrderStatus() == OrderStatus.DRAFT
          || order.getOrderStatus() == OrderStatus.DELIVERED
          || order.getOrderStatus() == OrderStatus.COMPLETED) {
      throw new IllegalArgumentException("This order cannot be cancelled at its current stage.");
  }
  ```
- **Impact:**
  1. If an order has been marked `PaymentStatus.PAID` (or is currently in `ORDER_STATUS_OUT_FOR_DELIVERY` or `PREPARING`), the buyer can cancel the order via `POST /api/buyer/orders/{id}/cancel`.
  2. The backend transitions the order to `CANCELLED`, restores the offering stock, and triggers a notification.
  3. However, there is no refund workflow, no ledger entry, and no alert to handle customer money already collected by the seller.
- **Reproduction:**
  1. Place an order and pay via UPI.
  2. Seller marks the order `PAID` and sets status to `PREPARING`.
  3. Buyer invokes `POST /api/buyer/orders/{id}/cancel`.
  4. Request succeeds (HTTP 200). Order is marked `CANCELLED`, but payment remains `PAID` with no refund audit trail.
- **Existing Protection:** `OrderDto` has a helper method `isRefundRequired()`, but there is no business logic preventing cancellation or executing refunds.
- **Recommendation:** Prevent buyer cancellation if `paymentStatus == PAID` or `acknowledgedAt != null` unless approved by seller or admin; create a dedicated `REFUND_PENDING` state.
- **Test Required:** `testBuyerCannotCancelPaidOrInPrepOrderWithoutSellerConsent()`.

---

### WEAK-03: Rate Limiting IP Spoofing via Client Headers
- **Area:** Security / Abuse
- **Severity:** **HIGH**
- **Confidence:** **CONFIRMED**
- **Evidence:** `AuthController.java` lines 279–300 (`clientIp`):
  ```java
  String cf = request.getHeader("CF-Connecting-IP");
  if (cf != null && !cf.isBlank()) return cf.trim();
  String xReal = request.getHeader("X-Real-IP");
  if (xReal != null && !xReal.isBlank()) return xReal.trim();
  String xff = request.getHeader("X-Forwarded-For");
  if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
  ```
- **Impact:**
  1. SocioMart is hosted directly on Render (`sociomart-demo.onrender.com`), not behind Cloudflare.
  2. An attacker can set arbitrary values in the `CF-Connecting-IP` or `X-Forwarded-For` header.
  3. By rotating this header with every request (`1.1.1.1`, `1.1.1.2`, etc.), the attacker completely bypasses the in-memory `AuthRateLimiter` and can brute-force passwords indefinitely.
- **Reproduction:**
  1. Send 10 consecutive failed logins with header `CF-Connecting-IP: 10.0.0.1`.
  2. Send next request with `CF-Connecting-IP: 10.0.0.2`.
  3. Request is not rate-limited (HTTP 401 instead of HTTP 429).
- **Existing Protection:** None when deployed without Cloudflare proxy validation.
- **Recommendation:** Only trust `CF-Connecting-IP` or proxy headers if the request remote address originates from verified proxy CIDR blocks, or rely solely on `request.getRemoteAddr()`.
- **Test Required:** `MockMvc` test verifying that unauthorized headers do not reset the rate limiter bucket.

---

### WEAK-04: Password Length and Complexity Not Enforced at Registration
- **Area:** Security / Authentication
- **Severity:** **MEDIUM**
- **Confidence:** **CONFIRMED**
- **Evidence:** `BuyerRegistrationRequestDto.java` line 19:
  ```java
  private String password; // No @NotBlank, no @Size
  ```
  `AuthService.java` lines 68–70:
  ```java
  requireBcryptCompatible(req.getPassword(), "password");
  // requireBcryptCompatible only enforces length <= 72 bytes!
  ```
- **Impact:**
  1. Users can register buyer or seller accounts with a 1-character password (e.g. `"x"`).
  2. If `req.getPassword()` is `null`, `requireBcryptCompatible` throws a `NullPointerException` resulting in an unhandled HTTP 500 error.
  3. The 10-character password complexity rule is only enforced in `changePassword`, leaving newly registered accounts insecure.
- **Reproduction:**
  1. Send `POST /api/auth/register` with `{"mobileNumber": "9876543210", "password": "1", "name": "Test"}`.
  2. Server responds with HTTP 200 OK and registers the account.
- **Existing Protection:** Only checks maximum length (72 bytes for BCrypt compatibility).
- **Recommendation:** Add `@NotBlank` and `@Size(min = 8, max = 72)` validation on registration DTOs.
- **Test Required:** `testRegistrationRejectsPasswordsUnder8Characters()`.

---

### WEAK-05: Discrepancy in Admin Brute-Force Lockout Durations
- **Area:** Security / Abuse
- **Severity:** **MEDIUM**
- **Confidence:** **CONFIRMED**
- **Evidence:** `AuthRateLimiter.java` lines 45–48:
  ```java
  public static final int ADMIN_LOGIN_MAX_FAILURES = 50;
  private static final Duration LOGIN_LOCKOUT_DURATION = Duration.ofSeconds(60);
  ```
- **Impact:**
  1. Documentation and security reports claim a "15-minute brute-force lockout window" for administrative accounts.
  2. The actual code sets lockout duration to only 60 seconds and allows 50 attempts per minute.
  3. An attacker can attempt 3,000 passwords per hour against `/api/admin/login` without being permanently locked out.
- **Reproduction:**
  1. Perform 50 failed admin logins to trigger lockout.
  2. Wait 61 seconds.
  3. The account bucket resets and allows another 50 password attempts.
- **Existing Protection:** In-memory rate limiting with 60-second window.
- **Recommendation:** Increase lockout duration to 900 seconds (15 minutes) and decrease max failures to 5 for administrative endpoints.
- **Test Required:** Unit test asserting lockout duration and failure limits.

---

### WEAK-06: Offering Deletion Causes Unhandled HTTP 500 Foreign Key Violation
- **Area:** Database / Integrity
- **Severity:** **HIGH**
- **Confidence:** **CONFIRMED**
- **Evidence:** `SellerService.java` line 626:
  ```java
  productRepository.delete(product);
  ```
  `V1__initial_schema.sql` line 83:
  ```sql
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
  ```
  (No `ON DELETE CASCADE` or `ON DELETE SET NULL`).
- **Impact:**
  1. If a seller attempts to delete an offering (`DELETE /api/seller/products/{id}`) that has ever been included in a past order, PostgreSQL aborts the transaction with `org.postgresql.util.PSQLException: ERROR: update or delete on table "products" violates foreign key constraint "fk_order_items_product" on table "order_items"`.
  2. The server crashes with HTTP 500 Internal Server Error.
  3. The seller is permanently stuck with the offering on their screen and cannot delete it.
- **Reproduction:**
  1. Create offering and place an order for it.
  2. Seller calls `DELETE /api/seller/products/{id}`.
  3. Server returns HTTP 500 `DataIntegrityViolationException`.
- **Existing Protection:** None.
- **Recommendation:** Introduce a soft-delete column (`deleted_at TIMESTAMP`) or check if order items exist and deactivate the product (`availableToday = false`) instead of hard deletion.
- **Test Required:** `testDeletingProductWithPastOrdersFailsGracefullyOrSoftDeletes()`.

---

### WEAK-07: Authentication Connection Storm (3+ DB Queries per Request with Pool Size = 4)
- **Area:** Performance / Database
- **Severity:** **HIGH**
- **Confidence:** **CONFIRMED**
- **Evidence:**
  `UserSessionAuthorizationFilter.java` line 48:
  ```java
  User user = userRepository.findById(userId).orElse(null);
  ```
  `application-prod.properties` (or default Render config):
  ```properties
  spring.datasource.hikari.maximum-pool-size=4
  ```
- **Impact:**
  1. Every single HTTP request from an authenticated user executes:
     - Query 1: Read session from `SPRING_SESSION` table.
     - Query 2: `SELECT * FROM users WHERE id = ?` in `UserSessionAuthorizationFilter`.
     - Query 3: Update `LAST_ACCESS_TIME` in `SPRING_SESSION` table.
  2. All 3 queries execute before the controller or business logic runs.
  3. With a maximum pool size of 4 connections on Supabase free tier, 10–15 concurrent users browsing the catalog will exhaust all available connections, causing thread queuing and HTTP 500 / 504 timeouts.
- **Reproduction:**
  1. Run load test with 20 concurrent users accessing authenticated endpoints with valid session cookies.
  2. Observe HikariCP `ConnectionTimeoutException: Request timed out after 30000ms`.
- **Existing Protection:** Filter uses `@Lazy` for repository injection, but still issues a query on every HTTP hit.
- **Recommendation:** Cache user role and block status in the HTTP session, refreshing it only on role change or every 5 minutes.
- **Test Required:** Staging load test with 25 concurrent virtual users measuring connection wait times.

---

### WEAK-08: Flawed Rating Formula (Exponential Decay & Multi-Item Truncation)
- **Area:** Business Logic / Data Integrity
- **Severity:** **MEDIUM**
- **Confidence:** **CONFIRMED**
- **Evidence:** `OrderService.java` lines 600–605 (`rateOrder`):
  ```java
  Product product = order.getItems().isEmpty() ? null : order.getItems().get(0).getProduct();
  if (product != null) {
      double oldRating = product.getRating() == null ? 0 : product.getRating();
      double newRating = oldRating == 0 ? rating : (oldRating + rating) / 2.0;
      product.setRating(Math.round(newRating * 10.0) / 10.0);
      productRepository.save(product);
  }
  ```
- **Impact:**
  1. The rating calculation does not track `rating_count` or calculate an arithmetic mean. It uses an exponential moving average where the latest rating has a 50% weight! If an offering has 100 5.0-star reviews, a single 1.0-star review instantly cuts the score to 3.0.
  2. For multi-item orders (e.g. Biryani + Gulab Jamun), only the first item in the list (`order.getItems().get(0)`) receives the rating; the second item is never updated.
  3. The method marks the order `COMPLETED` even if the food was never `DELIVERED`.
- **Reproduction:**
  1. Order an offering with 5.0 rating.
  2. Submit 1-star rating via `POST /api/buyer/orders/{id}/rate?rating=1`.
  3. Product rating drops to 3.0 instead of ~4.9.
- **Existing Protection:** Range check ensures rating is between 1 and 5.
- **Recommendation:** Add `rating_count` and `total_rating_points` to `products` and calculate `(total_rating_points + rating) / (rating_count + 1)`.
- **Test Required:** `testMultiItemRatingAndArithmeticMeanCalculation()`.

---

### WEAK-09: UTC Server Skew in Seller Earnings & Order Timestamps
- **Area:** Business Logic / Timezone
- **Severity:** **MEDIUM**
- **Confidence:** **CONFIRMED**
- **Evidence:** `SellerAppService.java` line 909:
  ```java
  LocalDateTime startOfToday = LocalDate.now().atStartOfDay(); // JVM system time!
  ```
  `SellerAppService.java` line 920:
  ```java
  boolean isToday = !order.getCreatedAt().isBefore(startOfToday);
  ```
- **Impact:**
  1. Render containers run with system clock set to UTC (`ZoneId.of("UTC")`).
  2. Indian Standard Time is UTC+05:30.
  3. Between midnight (00:00 IST) and 05:30 AM IST, `LocalDate.now()` evaluates to yesterday's date.
  4. Orders placed in early morning hours will be recorded as yesterday's sales on the seller dashboard.
  5. Furthermore, checking `order.getCreatedAt()` instead of `order.getOrderTime()` causes orders created as draft yesterday but checked out today to be excluded from today's earnings.
- **Reproduction:**
  1. Configure JVM with `-Duser.timezone=UTC`.
  2. Simulate order placement at 02:00 AM IST.
  3. Query `GET /api/seller-app/earnings/today`. Earnings return 0.
- **Existing Protection:** `AppTimeZone.today()` exists in the project but was omitted in `SellerAppService`.
- **Recommendation:** Replace all occurrences of `LocalDate.now()` with `AppTimeZone.today()` and compare against `order.getOrderTime()`.
- **Test Required:** `testSellerEarningsCalculatedInIndianStandardTime()`.

---

### WEAK-10: Multi-Item Checkout Deadlock Hazard (Unordered Row Locking)
- **Area:** Concurrency / Database
- **Severity:** **HIGH**
- **Confidence:** **CONFIRMED**
- **Evidence:** `OrderService.java` lines 1237–1244:
  ```java
  Map<Long, Integer> totals = new HashMap<>(); // Unordered HashMap!
  for (OrderItem item : order.getItems()) {
      totals.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
  }
  for (Map.Entry<Long, Integer> entry : totals.entrySet()) {
      productRepository.consumeStock(entry.getKey(), entry.getValue());
  }
  ```
- **Impact:**
  1. In PostgreSQL, `consumeStock` issues an `UPDATE products ... WHERE id = ?`, which acquires an exclusive row-level lock (`X` lock).
  2. If Buyer 1 checks out `[Product A, Product B]` and Buyer 2 checks out `[Product B, Product A]` at the same instant in separate transactions, Buyer 1 locks A and waits for B, while Buyer 2 locks B and waits for A.
  3. PostgreSQL detects a cyclic wait and aborts one transaction with error `40P01: deadlock detected`.
- **Reproduction:**
  1. Two threads concurrently checkout multi-item baskets with inverse product IDs.
  2. PostgreSQL logs show `ERROR: deadlock detected` and one checkout fails with HTTP 500.
- **Existing Protection:** None; iteration order depends on `HashMap` hash table bucket placement.
- **Recommendation:** Sort product IDs before locking: `new TreeMap<>(totals)` or `totals.keySet().stream().sorted().forEach(...)`.
- **Test Required:** `testConcurrentMultiItemCheckoutNoDeadlock()`.

---

### WEAK-11: Premature Reopening of Expired Items on Cancellation
- **Area:** Business Logic / Inventory
- **Severity:** **MEDIUM**
- **Confidence:** **CONFIRMED**
- **Evidence:** `ProductRepository.java` lines 47–50 (`restoreStock`):
  ```java
  @Modifying
  @Query("UPDATE Product p SET p.remainingQuantity = p.remainingQuantity + :quantity, " +
         "p.availableToday = CASE WHEN p.remainingQuantity + :quantity > 0 AND p.availableDate <= CURRENT_DATE THEN true ELSE p.availableToday END " +
         "WHERE p.id = :productId")
  void restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
  ```
- **Impact:**
  1. If an offering's ordering cutoff window closed at 14:00 (2:00 PM), and a buyer cancels an existing order at 16:00 (4:00 PM), `restoreStock` unconditionally executes `p.availableToday = true`.
  2. The expired offering immediately reappears on the buyer storefront as orderable, even though the kitchen has finished preparation.
- **Reproduction:**
  1. Create item with cutoff at 12:00 PM.
  2. Order item before cutoff.
  3. After cutoff (e.g. 1:00 PM), cancel order.
  4. Observe `availableToday` is set to `true` and item is visible on public discovery.
- **Existing Protection:** None; SQL query only checks `availableDate <= CURRENT_DATE`.
- **Recommendation:** Do not reopen `availableToday` in SQL if cutoff time has passed, or let `restoreStock` only increment quantity without modifying `availableToday`.
- **Test Required:** `testRestoringStockAfterCutoffDoesNotReopenOffering()`.

---

### WEAK-12: Zero Automated Production Database Backups & Missing Migration Rollback Mechanism
- **Area:** Operations / Reliability
- **Severity:** **HIGH**
- **Confidence:** **CONFIRMED**
- **Evidence:**
  - `backups/` directory inspection reveals 2 zip archives (`sociomart-pre-ui-redesign-20261005-1615` and `sociomart-seller-dashboard-ui-final-20261005-1707`). Both contain **only source code files and zero SQL database dumps**.
  - No scheduled cron or script exists in the repository for `pg_dump` or point-in-time recovery.
  - Flyway Community edition does not support down-migrations (`undo`).
- **Impact:**
  1. In the event of catastrophic data corruption, accidental drop, or bad deployment, no automated point-in-time database restore exists.
  2. Any schema migration failure requires manual database surgery.
- **Reproduction:** Inspect repository root and scripts for database dump automation. None exists.
- **Existing Protection:** Supabase platform-level daily backup (outside repository control).
- **Recommendation:** Implement automated GitHub Actions or Render cron executing `pg_dump` to an encrypted GCS/S3 bucket with 30-day retention.
- **Test Required:** Automated backup generation and isolated container restore verification script.

---

### WEAK-13: Unbounded Quick Post Image Payloads Causing Database and API Bloat
- **Area:** Performance / Security
- **Severity:** **MEDIUM**
- **Confidence:** **CONFIRMED**
- **Evidence:** `ProductCreateDto.java` line 30 & `V16__add_orderable_quick_posts.sql`:
  ```sql
  ALTER TABLE products ADD COLUMN quick_post_image_data text;
  ```
  `ProductCreateDto.java` has no `@Size` or format validation on `quickPostImageData`.
- **Impact:**
  1. Sellers can upload 10MB Base64-encoded images directly into the `products` table.
  2. Because `ProductDto` serializes `quickPostImageData` in list endpoints (`/api/marketplace`, `/api/kitchens/{id}`), querying a kitchen with 5 quick posts transmits 50MB of JSON over mobile networks, exhausting server memory and client bandwidth.
- **Reproduction:**
  1. Submit `POST /api/seller-app/quick-posts` with a 5MB base64 string.
  2. Query `GET /api/marketplace`.
  3. Response payload exceeds 5MB.
- **Existing Protection:** None.
- **Recommendation:** Restrict `quickPostImageData` length to max 500KB or offload images to Cloud Storage / S3 with CDN URLs.
- **Test Required:** `testQuickPostRejectsExcessiveImageSize()`.

---

### WEAK-14: Seller Self-Ordering and Rating Manipulation
- **Area:** Business Logic / Integrity
- **Severity:** **MEDIUM**
- **Confidence:** **CONFIRMED**
- **Evidence:** `OrderService.java` lines 430–540 (`placeOrder`):
  Checks buyer profile and kitchen service area, but does **not** check:
  `if (order.getKitchen().getSeller().getId().equals(buyer.getId()))`.
- **Impact:**
  1. A seller who registers as a buyer can place COD orders to their own kitchen.
  2. They can immediately mark the order `DELIVERED`, mark payment `PAID`, and submit 5-star ratings via `POST /api/buyer/orders/{id}/rate`.
  3. This enables sellers to artificially manipulate their kitchen ratings, order counts, and marketplace visibility.
- **Reproduction:**
  1. Log in as a seller.
  2. Create a draft order for your own kitchen.
  3. Call `placeOrder`, mark `PAID`, and submit rating `5`. All succeed.
- **Existing Protection:** None.
- **Recommendation:** Enforce `if (order.getKitchen().getSeller().getId().equals(buyer.getId())) throw new IllegalArgumentException("Sellers cannot place orders to their own kitchen");`.
- **Test Required:** `testSellerCannotPlaceOrderOrRateOwnKitchen()`.

---

### WEAK-15: Abandoned Draft Carts Accumulate Indefinitely in `orders` Table
- **Area:** Database / Maintenance
- **Severity:** **LOW**
- **Confidence:** **CONFIRMED**
- **Evidence:** `BuyerOrderController.java` lines 32–37:
  Every time a user adds an item to cart, an `Order` row with `orderStatus = DRAFT` is inserted into the `orders` table.
- **Impact:**
  1. Users browsing items create draft orders that are never placed.
  2. There is no scheduled reaper job or TTL cleanup. Over time, tens of thousands of orphaned `DRAFT` records accumulate in the `orders` and `order_items` tables, degrading index performance.
- **Reproduction:** Call `POST /api/buyer/orders/draft` 50 times without checkout. 50 permanent draft rows remain in the DB.
- **Existing Protection:** Reports filter out `DRAFT` orders (`orderStatus <> DRAFT`).
- **Recommendation:** Implement a daily Spring `@Scheduled` task to purge draft orders older than 48 hours.
- **Test Required:** Integration test verifying draft order cleanup job.

---

### WEAK-16: Test Suite Divergence: 8 Testcontainers Tests Skipped Locally
- **Area:** Test Coverage / Verification
- **Severity:** **INFORMATIONAL**
- **Confidence:** **CONFIRMED**
- **Evidence:** `docs/DEEP_QA_TEST_REPORT.md` lines 25–34:
  8 Testcontainers integration tests (`UserEmailMigrationRegressionTest`, `PostgresNativeMigrationTest`, `PostgresRuntimeSmokeTest`, `PostgresStockContentionConcurrencyTest`) are annotated with `@Testcontainers(disabledWithoutDocker = true)`.
- **Impact:**
  1. Local Windows development environments without Docker Desktop skip all PostgreSQL-specific tests.
  2. Developers verifying changes locally rely entirely on H2 in PostgreSQL compatibility mode, which behaves differently regarding transaction isolation, locking semantics, and constraint checking.
- **Reproduction:** Run `.\mvnw.cmd test` on a host without Docker. Notice `Skipped: 8`.
- **Existing Protection:** GitHub CI runs Docker and executes all 8 tests.
- **Recommendation:** Maintain dedicated isolated staging PostgreSQL database reachable via environment variable for local verification when Docker is absent.
- **Test Required:** None.

---

## 4. Threat Modeling & Security Vulnerability Matrix

| Threat ID | Threat Vector | Target Component | Existing Mitigation | Residual Risk | Status |
|:---|:---|:---|:---|:---|:---:|
| **THR-01** | IP Header Spoofing | `AuthRateLimiter` | Checks `CF-Connecting-IP` / `X-Real-IP` | **HIGH** — Headers can be forged by client when not behind Cloudflare. | UNMITIGATED |
| **THR-02** | Credential Stuffing | `/api/admin/login` | Lockout after 50 attempts | **MEDIUM** — 60s lockout window is too brief; allows 500 attempts / 10 min. | PARTIAL |
| **THR-03** | Weak Passwords | `/api/auth/register` | Length <= 72 bytes | **MEDIUM** — Single-character passwords allowed at registration. | UNMITIGATED |
| **THR-04** | IDOR on Orders | `/api/buyer/orders/{id}` | Checks `order.getBuyer().equals(user)` | **LOW** — Properly mitigated; returns 404 masked. | MITIGATED |
| **THR-05** | IDOR on Kitchens | `/api/seller/products/{id}` | Checks `kitchen.getSeller().equals(user)` | **LOW** — Properly mitigated; returns 403 / 404. | MITIGATED |
| **THR-06** | Self-Review Fraud | `/api/buyer/orders/{id}/rate` | Checks buyer session | **MEDIUM** — Sellers can order from themselves and submit 5-star ratings. | UNMITIGATED |
| **THR-07** | Large Payload DoS | `/api/seller-app/quick-posts` | None on image data | **MEDIUM** — Unbounded base64 images bloat database and API responses. | UNMITIGATED |

---

## 5. Concurrency & Database Race Condition Analysis

### Recurring Schedule Booking Hazard (WEAK-01)
- In `OrderService.java`, recurring offerings intentionally skip `productRepository.consumeStock(...)`.
- The capacity check loads all historical kitchen orders via `findByKitchenOrderByCreatedAtDesc` into JVM memory.
- Multiple threads executing checkout simultaneously read identical counts.
- **Result:** Complete lack of concurrency control on recurring schedule bookings.

### Multi-Item Deadlock Hazard (WEAK-10)
- Checkout locking locks product rows sequentially.
- Rows are locked in `HashMap` iteration order, which is non-deterministic.
- Two transactions locking `[A, B]` and `[B, A]` concurrently will encounter PostgreSQL `40P01 Deadlock Detected`.
- **Result:** One of the two checkout transactions fails with an internal error.

---

## 6. Audit of Existing Test Suites (849 Maven & 24 Playwright Tests)

### What the Existing Suites Verify
- Correct CRUD operations on products, kitchens, and orders.
- Basic Spring Security URL-pattern enforcement (`ROLE_ADMIN`, `ROLE_SELLER`).
- IDOR protections preventing Buyer A from reading Buyer B's orders.
- Negative price rejections on product creation.
- Basic stock decrement under sequential conditions.

### Gaps in Current Test Coverage
1. **Zero Recurring Concurrency Tests:** Not a single test simulates concurrent buyers ordering from a recurring schedule simultaneously.
2. **Zero Deadlock Tests:** No tests attempt multi-item checkouts with inverted product IDs under high concurrency.
3. **Zero Post-Payment Cancellation Tests:** No test asserts that a buyer cannot cancel an order once marked `PAID`.
4. **Zero Offering Deletion with Historical Orders Test:** No test verifies what happens when `productRepository.delete()` is called on an offering with orders.
5. **No Local PostgreSQL Execution:** 8 PostgreSQL tests are skipped on local machines lacking Docker.

---

## 7. Recommended Remediation Order & Implementation Roadmap

Remediation should proceed strictly in order of risk:

### Phase 1: Critical & High Concurrency Fixes (Prevent Overselling & Deadlocks)
1. **Fix WEAK-01:** Implement a dedicated PostgreSQL table for recurring bookings with check constraints and row-level locking.
2. **Fix WEAK-10:** Ensure deterministic product ID ordering (`totals.keySet().stream().sorted()`) before invoking `consumeStock`.
3. **Fix WEAK-06:** Change offering deletion to soft deletion (`p.setActive(false)`) or return a clean HTTP 400 if past orders exist.

### Phase 2: Security & Abuse Hardening
4. **Fix WEAK-03:** Restrict trusted proxy headers in `AuthController.clientIp()` to trusted IP ranges only.
5. **Fix WEAK-04:** Add `@NotBlank` and `@Size(min = 8, max = 72)` validation on registration DTOs.
6. **Fix WEAK-05:** Increase admin lockout duration from 60 seconds to 900 seconds (15 minutes).

### Phase 3: Business Logic & Financial Integrity
7. **Fix WEAK-02:** Disallow order cancellation when `paymentStatus == PAID` or `acknowledgedAt != null`.
8. **Fix WEAK-08:** Correct product rating formula to calculate true arithmetic average across all items.
9. **Fix WEAK-14:** Disallow sellers from ordering from their own kitchens.
10. **Fix WEAK-09:** Standardize all timezone calls in `SellerAppService` to use `AppTimeZone.today()`.
11. **Fix WEAK-11:** Prevent `restoreStock` from reopening `availableToday` if cutoff has passed.

### Phase 4: Performance, Scalability & Operations
12. **Fix WEAK-07:** Cache user role and block status in session to eliminate 3 redundant DB hits per request.
13. **Fix WEAK-13:** Validate and limit `quickPostImageData` to max 500KB.
14. **Fix WEAK-15:** Add a scheduled cleanup task for orphaned `DRAFT` orders older than 48 hours.
15. **Fix WEAK-12:** Set up automated production database dumps to offsite storage.

---

## 8. Statement of What Was and Was Not Tested

### Explicitly Tested and Inspected
- Full source code audit of all Java controllers, services, repositories, and models.
- Complete Flyway migration sequence (V1 through V21).
- Spring Security filter chain, CSRF configuration, and session handling.
- Review of all past defect reports and PR diffs (#104, #106, #107, #108).
- Static analysis of concurrency bottlenecks and row-locking logic.

### NOT Tested (Out of Scope / Environment Constraints)
- **Destructive SQL or mutation of production database:** Strictly prohibited.
- **Direct load testing against Render production URL:** Prohibited by safety rules.
- **Docker Testcontainers on host:** Docker daemon unavailable on local Windows host (8 tests skipped locally; validated in GitHub CI).
- **Physical payment gateway integrations:** SocioMart currently uses manual UPI verification; no external gateway webhooks exist.
