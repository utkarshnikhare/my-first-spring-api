# Backend Error & Defect Audit Report

## 1. Executive Summary

| Field | Value |
|---|---|
| Audit date | 2026-10-02 |
| Commit SHA | `3f7931e2ba811db5d39b403f10cdd9042ccf8bcd` |
| Branch | `main` (HEAD = origin/main, ahead/behind `0 0`) |
| Backend scope | Spring Boot 4.1.1 monolith, Java 21 — 14 controllers, 15 services, 17 repositories, 26 entities, 33 DTOs, 9 exceptions |
| Maven command | `.\mvnw.cmd -B clean verify` |
| Test result | **391 run / 391 passed / 0 failed / 0 errors / 0 skipped — BUILD SUCCESS** (56 classes, 02:26) |
| Confirmed defects | **22** |
| Risks | **7** |
| Recommendations | **12** |

**Overall finding.** The domain layer is genuinely well built — `KitchenVisibility` is a correctly centralised, well-documented rule engine; the Area/Society master uses ID-backed relationships with server-side validation; order-number generation has a DB-level uniqueness backstop; session fixation is properly mitigated; the CI gate genuinely runs the full suite.

The defects cluster in three places: **identity** (there is no authentication, only a demo-login that assigns roles from the request body), **production readiness** (the `prod` profile cannot start and no data persists), and **unbounded data access** (28 `findAll()` call sites with pagination on only three bounded paths).

**Nothing in this audit was fixed.** This report is evidence only.

## 2. Repository Baseline

| Check | Value |
|---|---|
| Branch | `main` |
| HEAD | `3f7931e2ba811db5d39b403f10cdd9042ccf8bcd` |
| origin/main | identical, ahead/behind `0 0` |
| Working tree | ` M .vscode/settings.json` only (pre-existing, protected) |
| `.vscode/settings.json` SHA256 | `1D931C42CD3C3D0E0FE21C0C52E9853BE9A4A34C9DCBDDE135E68E16F1959E68` — unchanged |

## 3. Backend Inventory

| Layer | Count | Files |
|---|---:|---|
| Entrypoint | 1 | `MyFirstSpringApiApplication` |
| Controllers | 14 | `AdminController`, `SuperAdminController`, `AuthController`, `BuyerOrderController`, `BuyerProfileController`, `BuyerPublicController`, `DiscoveryController`, `EnquiryController`, `FavouriteController`, `MarketplaceController`, `NotificationController`, `SellerAppController`, `SellerController`, `AnalyticsController` |
| Services | 15 | incl. `AdminService` (~1150 lines, multi-responsibility), `OrderService`, `SellerService`, `LocationService`, `KitchenVisibility` (static rule engine), `DiscoveryService`, `MarketplaceService`, `LedgerService` |
| Repositories | 17 | Spring Data JPA |
| Entities | 26 | `User`, `Kitchen`, `Product`, `Order`, `OrderItem`, `Enquiry`, `Favourite`, `Area`, `Society`, `Category`, `NotificationEvent`, `AnalyticsEvent`, `LedgerEvent`, `FeatureFlag`, `SellerFeatureGrant`, `SellerTemplate`, `QuickPost`, `PlatformSetting`, + enums |
| DTOs | 33 | — |
| Exceptions | 9 | mapped centrally in `GlobalExceptionHandler` |
| Security | 2 | `SecurityConfig`, `UserSessionAuthorizationFilter` |
| Seed/init | 2 | `DataInitializer`, `DemoDataSeeder` (both `@Profile({"demo","dev","default"})`) |
| Scheduled jobs | 1 | `NotificationReminderJob` |
| Tests | 56 classes | 391 tests |

## 4. Build & Test Results

Command: `.\mvnw.cmd -B clean verify`

| Metric | Value |
|---|---|
| Tests run | **391** |
| Passed | **391** |
| Failed | **0** |
| Errors | **0** |
| Skipped | **0** |
| Build | **BUILD SUCCESS** |
| Artifact | `my-first-spring-api-0.0.1-SNAPSHOT.jar` (62,511 KB) |
| Total time | 02:26 |

No correctness-relevant compiler warnings were emitted. The build is clean; the defects below are logical, not compilation-level.
## 5. Confirmed Defects

| ID | Sev | Category | Title | File | Location |
|----|-----|----------|-------|------|----------|
| S-01 | P0 | Security | No authentication — request body selects the role | `AuthController.java` | L51-77 |
| S-02 | P1 | Security | H2 console + Swagger/OpenAPI `permitAll` in every profile | `SecurityConfig.java` | L60-82 |
| S-03 | P1 | Security | No rate limiting, lockout, or attempt tracking | (repository-wide) | — |
| S-04 | P2 | Security | Unauthenticated analytics write stores raw mobile PII | `AnalyticsController` / `AnalyticsEvent` | L26-44 / L30 |
| S-05 | P2 | Security | Catch-all handler logs full stack traces | `GlobalExceptionHandler.java` | L125-129 |
| F-01 | P2 | Functional | Invalid `category` returns ALL data under a false label | `DiscoveryController.java` | L104-111, L66 |
| F-02 | P2 | Functional | Display **name** used as a path identifier | `MarketplaceController.java` | L45-55 |
| F-03 | P2 | Functional | Two parallel search endpoints, different shapes | `MarketplaceController` / `DiscoveryController` | L39-43 / L80-84 |
| F-04 | P2 | Functional | Integrity exception swallowed, then `flush()` re-throws | `FavouriteService.java` | L59-64 |
| F-05 | P3 | Functional | `@Valid` on a raw `Map` is a no-op | `AuthController.java` | L52 |
| A-01 | P2 | API | Two error-body shapes from one advice | `GlobalExceptionHandler.java` | L37-46 vs L61+ |
| A-02 | P2 | API | Unconditional `ObjectError`→`FieldError` cast | `GlobalExceptionHandler.java` | L41 |
| A-03 | P2 | API | 12 unvalidated `Map` bodies; profile update lacks `@Valid` | controllers | multiple |
| A-04 | P2 | API | Unguarded `Long.valueOf` on request body | `AnalyticsController.java` | L37 |
| D-01 | P2 | Data | Max-3-favourites limit is a TOCTOU race | `FavouriteService.java` | L52-55 |
| D-02 | P2 | Data | No cascade/orphanRemoval outside `Order.items` | entities | model/* |
| P-01 | P0 | Deployment | `prod` profile cannot start | `application-prod.properties` | L10-14 |
| P-02 | P0 | Deployment | No admin in `prod` — bootstrap deadlock | `DataInitializer.java` | L19, L36 |
| P-03 | P0 | Deployment | All Render data ephemeral | `render.yaml` / demo props | — |
| P-04 | P1 | Deployment | No PostgreSQL/MySQL driver on classpath | `pom.xml` | — |
| P-05 | P2 | Deployment | No migration tooling; `validate` unbacked | `pom.xml` / prod props | — |
| P-06 | P2 | Deployment | 15 × `columnDefinition="TEXT"` portability risk | 12 entities | — |

### 5.1 S-01 — No authentication (P0)

**Location:** `AuthController.demoLogin` L51-77; `BuyerService.demoLoginAndAuthenticate`.
**Expected:** a user proves identity before receiving a session.
**Actual:** `POST /api/auth/demo-login` accepts `{mobileNumber}` and issues a session for whichever role that mobile already holds, creating a new BUYER for unknown numbers. `User` has **no** password, OTP, token or external-IdP field (15 fields, none credential-related).
**Impact:** possession of a mobile number *is* the credential. Knowing `9000000002` grants ADMIN. There is no secret to rotate.
**Evidence:** `SecurityConfig` L56-57 gates the endpoint on the demo profile — which is the profile Render actually runs (`SPRING_PROFILES_ACTIVE=demo`).
**Test coverage:** none asserts an unprivileged caller is rejected.
**Fix direction:** introduce a credential behind a profile flag; keep demo-login demo-only.
**Confidence:** High.

### 5.2 S-02 — H2 console and API docs publicly reachable (P1)

**Location:** `SecurityConfig` L60-82 — `permitAll` includes `/h2-console/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`.
**Actual:** these sit in the global `permitAll()` list and apply in **every** profile. `application-prod.properties` sets `spring.h2.console.enabled=false`, which disables the console without protecting the route; the API documentation stays fully public and enumerates the whole endpoint surface.
**Impact:** full schema/API disclosure in production; the console becomes an exfiltration path if ever re-enabled.
**Fix direction:** move these matchers behind the existing demo gate.
**Confidence:** High.

### 5.3 S-03 — No rate limiting, lockout, or attempt tracking (P1)

Repository-wide search returns **zero** matches for `RateLimiter|Bucket4j|Throttle|lockout|failedAttempts|Captcha`. Both the login endpoint and the anonymous analytics endpoint are floodable, and there is no defence when real credentials are introduced. **Confidence:** High.

### 5.4 S-04 / S-05 — PII write and stack-trace logging (P2)

`POST /api/analytics/event` is `permitAll` and writes `AnalyticsEvent.user_mobile` (raw mobile) plus free-text `detail`. Event type is correctly whitelisted (L32-42, preventing arbitrary type injection) but the endpoint is not rate-limited. The catch-all handler (L125-129) logs full stack traces at ERROR — a PII channel once auth/DB exceptions carry identifiers. **Confidence:** High.
### 5.5 F-01 — Invalid `category` silently returns everything (P2)

**Location:** `DiscoveryController.parseCategory` L104-111, response build L66.
**Expected:** `?category=BOGUS` → 400 (invalid enum).
**Actual:** `parseCategory` catches `IllegalArgumentException` and returns `null`, which means "all categories". The response then echoes `"category": "BOGUS"` because L66 uses the raw request parameter rather than the parsed value. The caller receives a payload labelled `BOGUS` containing every category's items — a response that misrepresents its own contents.
**Reproduction:** `GET /api/discovery/items?category=BOGUS` → 200, `category:"BOGUS"`, unfiltered items.
**Fix direction:** reject unparseable values; echo the normalised value only when parsing succeeded.
**Confidence:** High.

### 5.6 F-02 — Display name used as a path identifier (P2)

**Location:** `MarketplaceController` L45-55 — `/api/kitchens/{kitchenName}`, `/api/kitchens/{kitchenName}/products`.
**Expected:** stable identifiers; `/api/kitchens/id/{id}` (L58) already does this correctly.
**Actual:** a mutable display string is part of the URL contract. A rename breaks every stored link; names containing `/`, `#` or spaces cannot round-trip; the route enumerates kitchen names. A kitchen whose display name is literally `id` also collides with `/api/kitchens/id/{id}`.
**Fix direction:** serve by ID only; keep a deprecated redirect if compatibility is required.
**Confidence:** High.

### 5.7 F-03 — Two parallel search APIs (P2)

`GET /api/search?q=` returns `SearchResultDto` (via `KitchenService.search`, `MarketplaceController` L39-43); `GET /api/discovery/search?q=` returns `List<ItemGroup>` (via `DiscoveryService.searchItemGroups`, `DiscoveryController` L80-84). Same purpose, different service, different response shape. Kitchens and items are likewise duplicated across `/api/kitchens`, `/api/items` and `/api/discovery/kitchens`, `/api/discovery/items`.
**Confidence:** High.

### 5.8 F-04 — Swallowed integrity exception, then `flush()` (P2)

**Location:** `FavouriteService.toggleKitchen` L59-64.
```java
try {
    favouriteRepository.save(f);
} catch (DataIntegrityViolationException ex) {
    favouriteRepository.flush();   // re-attempts the same failing statement
    return true;                   // reports success; exception never logged
}
```
**Expected:** a constraint violation is handled or propagated with a clear error.
**Actual:** the catch re-invokes `flush()` on a persistence context that still holds the offending entity, which re-throws; nothing is logged; and the method signals success. The `try/catch` provides no recovery and destroys the only diagnostic.
**Impact:** concurrent duplicate-favourite requests can produce an opaque 500 with no log evidence.
**Fix direction:** drop the catch (the unique constraint already prevents duplicates) or rethrow as a mapped conflict.
**Confidence:** High.

### 5.9 A-01 / A-02 — Error contract inconsistency and unsafe cast (P2)

**A-01:** `handleValidationExceptions` (L37-46) returns a bare `Map<String,String>` of field→message, while every other handler returns `ApiErrorDto`. Clients must parse two JSON shapes from one service.
**A-02:** L41 casts every binding error to `FieldError`:
```java
String fieldName = ((FieldError) error).getField();
```
`getAllErrors()` returns `ObjectError`; a class-level constraint (e.g. `@AssertTrue` on a DTO) yields a plain `ObjectError` and would throw `ClassCastException` **inside the exception handler**, converting a 400 into a 500.
**Fix direction:** return `ApiErrorDto` consistently; use `instanceof FieldError` before casting.
**Confidence:** High for A-01; Medium-High for A-02 (latent — no class-level constraint currently exists).
## 6. Security Findings

### Confirmed vulnerabilities
- **S-01** No authentication (P0) — gated to the demo profile, but that is the deployed profile.
- **S-02** H2 console + Swagger public in all profiles (P1).
- **S-03** No rate limiting / lockout (P1).
- **S-04** `POST /api/analytics/event` is `permitAll` and stores raw mobile PII (P2).
- **S-05** Catch-all handler logs full stack traces (P2).

### Explicitly NOT defects (checked and cleared)
- **Session fixation** is **properly mitigated** — `SecurityConfig:93` configures `sessionFixation().migrateSession()` **and** `AuthController:60` calls `request.changeSessionId()`. Two independent controls present.
- **CSRF** is enabled outside demo/dev/default and the frontend complies (`a54fdf0`).
- **`KitchenVisibility` anonymous browsing** returns `true` by documented policy; order placement re-checks via `BuyerProfileIncompleteException`. Intentional, with rationale in the class javadoc.
- **Role derivation** is re-read from the database on every request by `UserSessionAuthorizationFilter`, so role changes apply immediately and cannot be forged in the cookie.
- **CSRF cookie** is issued correctly (`httpOnly=false`, path `/`).

### Hardening recommendations (not defects)
- No `Secure` flag on the session cookie in any profile; sessions are in-memory only.
- No security headers beyond `frameOptions(sameOrigin)`.
- No `X-Content-Type-Options` / CSP / HSTS configuration.

## 7. Database / Data Integrity Findings

- **D-01 (P2)** `FavouriteService.toggleKitchen` L52-55 enforces "max 3 favourites" with `countByUserId` then insert — a TOCTOU race; concurrent requests can exceed the limit.
- **D-02 (P2)** Only one cascade exists anywhere (`Order.items` `cascade=ALL, orphanRemoval=true`). `Favourite.kitchen_id` / `product_id` are nullable with no orphan handling, so removing a kitchen or product leaves dangling favourites.
- **Favourite uniqueness** — `@UniqueConstraint(columnNames={"user_id","kitchen_id"})` exists and is adequate for the implemented kitchen-only toggle. **Not a defect.**
- **F-05 (P3)** `User.mobileNumber` is unique but nullable, so it cannot serve as a production identity key.
- **Order numbering** — UUID candidate + `existsBy` retry (5 attempts) + DB unique backstop. Check-then-act exists but is correctly mitigated by the column-level `unique` constraint. **Not a defect.**
- **Transactional coverage** — `OrderService` carries a class-level `@Transactional`; all write services except `LedgerService` have class- or method-level coverage. `LedgerService` performs a single `save()`, which Spring Data makes transactional per call and which joins the caller's transaction. **Not a defect.**
- **Portability** — 15 × `columnDefinition="TEXT"` across 12 entities (P2 for MySQL: 64 KB cap, not indexable); 145 `LocalDateTime` mappings carry no timezone, so semantics drift across engines.
- **104 nullable `@Column` definitions** across the model — reviewed; the nullable ones are either optional metadata (`image_url`, `short_description`, timestamps) or intentionally nullable FKs (`acknowledged_by`, `product_id`). No defect identified.

## 8. API Findings

- **A-01 / A-02** two error shapes; unsafe `FieldError` cast (§5.9).
- **A-03** 12 controller methods accept raw `Map` request bodies with no validation. `BuyerProfileController.updateProfile` accepts a DTO **without** `@Valid`, so profile updates bypass bean validation entirely. `@Valid` appears on only 9 of the request-body sites.
- **A-04** `AnalyticsController` L37 calls `Long.valueOf(body.get("kitchenId"))` unguarded; a non-numeric value throws `NumberFormatException`, caught as `IllegalArgumentException` and surfaced as a generic 400 whose message is the raw parse error.
- **Duplicate surface** — see F-03. `MarketplaceController` is additionally the only controller without a class-level `@RequestMapping`.
- **Entity leakage** — several Admin responses are assembled from entity references; there is no uniform DTO boundary.

## 9. Business Logic Findings

- **F-01** invalid category returns unfiltered data under a false label (§5.5).
- **F-02 / F-03** name-based routing and duplicated read APIs (§5.6, §5.7).
- **F-04** swallowed integrity exception (§5.8).
- `KitchenVisibility`'s legacy string-fallback path (L98-132) is intentionally permissive; the location-less-kitchen branch returns `true` and is documented in-code as an **open product question**, not a defect.
## 11. Test Coverage Gaps

- **No test class** for controllers: `MarketplaceController`, `DiscoveryController`, `AnalyticsController`, `SuperAdminController`, `BuyerProfileController`, `BuyerPublicController`.
- **No test class** for services: `MarketplaceService`, `DiscoveryService`, `AnalyticsService`, `FeatureService`, `LedgerService`.
- Consequence: the **entire public/anonymous API surface** (marketplace, discovery, analytics, super-admin) has no automated coverage.
- No `prod`-profile test exists (0 of 56 classes), so the P-01/P-02 startup deadlock is invisible to CI.
- No concurrency tests for D-01, the favourite limit, or order-number generation.
- No test asserts that an anonymous or wrong-role caller is rejected on any Admin mutation.
- Positive note: the HTTP-level `AdminLocationMasterHttpTest` added in `a54fdf0` proves the security filter chain, role rules and CSRF are now covered for the Area/Society surface. That class does not exist for any other endpoint.

## 12. Performance Risks

| ID | Risk | Evidence | Sev |
|---|---|---|---|
| PR-01 | Unbounded full-table loads in Admin list/aggregate endpoints | 8 `findAll()` in `AdminService` (L64-67, 279, 387, 423, 532, 886) | P1 |
| PR-02 | Public hot path does full-table load + in-memory filtering | `MarketplaceService` L37, 58; `DiscoveryService` L58, 65, 290; `KitchenService` L138 | P2 |
| PR-03 | Indexes exist only on `AnalyticsEvent` | `Kitchen.society`, `Order.buyer`, `Enquiry.user` unindexed | P2 |
| PR-04 | 28 total `findAll()` sites vs only 12 bounded `Pageable` uses (limits 20 / 100 / 6) | repository-wide scan | P2 |

PR-01 aggregates in Java over complete result sets; heap grows linearly with total data. PR-02 means the marketplace's central business rule is evaluated in memory rather than in the database.

## 13. Maintainability Concerns

| ID | Concern | Sev |
|---|---|---|
| M-01 | `AdminService` ~1150 lines spanning dashboard, CRUD, approvals, analytics, traffic, locations | P2 |
| M-02 | Two parallel API read models (§8 / F-03) | P2 |
| M-03 | No uniform DTO boundary; entities returned directly from several endpoints | P3 |
| M-04 | `MarketplaceController` lacks class-level `@RequestMapping` (only controller that does) | P3 |
| M-05 | `@Valid` on a raw `Map` (F-05) misleads readers into believing validation exists | P3 |

## 14. Recommendations

R-01 Choose and implement an authentication strategy, preserving demo-login behind a profile gate.
R-02 Make the admin bootstrap idempotent and independent of demo-login.
R-03 Select a production database, add the driver, adopt a migration tool with a verified baseline.
R-04 Enable cookie `Secure`; adopt a shared session store only if horizontal scale is required.
R-05 Add security headers and a CSP; scope H2/Swagger matchers to non-production.
R-06 Add rate limiting to authentication and analytics endpoints.
R-07 Add tests for the six untested controllers and five untested services, starting with the anonymous public surface.
R-08 Introduce pagination on all Admin and public list endpoints; add the missing indexes.
R-09 Unify the error contract on `ApiErrorDto` and fix the `FieldError` cast.
R-10 Replace raw `Map` request bodies with validated DTOs; add `@Valid` to profile update.
R-11 Serve kitchens/products by ID; deprecate name-based routes; consolidate the duplicate read APIs.
R-12 Add a `prod`-profile context-load test so P-01/P-02 cannot regress unnoticed.

## 15. Defect Count Summary

| Category | Count |
|---|---:|
| Confirmed Security Defects | 5 |
| Confirmed Functional Defects | 5 |
| Confirmed API Contract Defects | 4 |
| Confirmed Data-Integrity Defects | 2 |
| Confirmed Deployment/Production Defects | 6 |
| **Total confirmed defects** | **22** |
| Performance Risks (not defects) | 4 |
| Maintainability Concerns (not defects) | 5 |
| Recommendations | 12 |

Arithmetic: 5 + 5 + 4 + 2 + 6 = **22**.

| Severity | Confirmed defects | Notes |
|---|---:|---|
| P0 — Critical | 4 | S-01, P-01, P-02, P-03 |
| P1 — High | 3 | S-02, S-03, P-04 |
| P2 — Medium | 14 | S-04, S-05, F-01…F-04, A-01…A-04, D-01, D-02, P-05, P-06 |
| P3 — Low | 1 | F-05 |
| **Total** | **22** | plus 4 performance risks (PR-01 is P1 severity) and 5 maintainability concerns |

## 16. Top Priority Fix Order

1. **Decide the architecture first** (auth, database, migration) — P-02 cannot be fixed before the auth decision; P-01/P-03 cannot be fixed before the database decision.
2. **P-01, P-03, P-04, P-05** — make production able to start and persist data.
3. **P-02** — bootstrap the first administrator without demo-login.
4. **S-01** — implement the chosen credential mechanism.
5. **S-02, S-03, S-05** — security hardening, now that a real credential exists.
6. **F-04, A-01, A-02, A-03, A-04** — correctness and contract fixes; independent, can run in parallel.
7. **F-01, F-02, F-03** — API correctness and consolidation.
8. **D-01, D-02** — data integrity.
9. **PR-01…PR-04** — performance, once real data volumes exist.
10. **Test gaps (R-07) and `prod`-profile test (R-12)** — add alongside each fix to prevent regression.

## 17. Audit Limitations

- **No live Render verification of runtime settings** — no Render CLI, API key (401), or dashboard session. Repository configuration was verified; live service configuration was not.
- **No authenticated GitHub API access** — branch-protection/ruleset state could not be read or written (401).
- **The production profile was never executed** — no `prod` deployment was attempted, so P-01/P-02 are established from configuration analysis, not from an observed failure.
- **No concurrency testing** — D-01 and the F-04 race are established by code reading, not by reproduction under load.
- **No database introspection** — index and constraint statements are read from entity mappings, not from a live schema.
- **Static analysis was manual** — no SpotBugs/PMD/dependency-vulnerability scan was run.
- A-02 is **latent**, not active: no class-level constraint currently exists, so the unsafe cast has not yet been triggered. Confidence is recorded as Medium-High for that reason.

---

*Audit only — no application source, test, configuration, or deployment file was modified. No commit, push, or deployment was performed.*
- Domain invariants verified as **correct** (explicitly cleared, not defects): `society.areaId` validated server-side; master records disabled rather than deleted; seller coverage stored as explicit Society IDs; `KitchenVisibility` is the single visibility engine; ownership checks present in `EnquiryService` (L94, L109) and `BuyerService` (L75).

## 10. Production / Deployment Findings

| ID | Finding | Sev |
|---|---|---|
| P-01 | `prod` cannot start against an empty database | P0 |
| P-02 | Admin bootstrap deadlock in `prod` | P0 |
| P-03 | All Render data ephemeral; no `disk` declared | P0 |
| P-04 | No PostgreSQL/MySQL driver; prod DB config commented out | P1 |
| P-05 | No Flyway/Liquibase; `validate` unbacked by a baseline | P2 |
| P-06 | 15 × `TEXT` columns across 12 entities | P2 |
| — | `render.yaml` `autoDeploy: false`; CI runs `mvn -B clean verify` | verified OK |

### 5.10 P-01 / P-02 / P-03 — Production cannot run (P0)

| ID | Evidence |
|---|---|
| **P-01** | `application-prod.properties` sets `ddl-auto=validate` + `spring.sql.init.mode=never`; there are **0** `*.sql` files and no `db/migration` directory. `validate` refuses to create missing tables, so the context fails at startup against an empty database. |
| **P-02** | `DataInitializer` is `@Profile({"demo","dev","default"})` and is the only caller of `ensureBootstrapAccounts()`. Under `prod` it does not run and demo-login is disabled — a `prod` deployment starts with **zero administrators and no way to create one**. |
| **P-03** | Render runs `SPRING_PROFILES_ACTIVE=demo`; `application-demo.properties` deliberately overrides **no** datasource and inherits `jdbc:h2:mem:`. `render.yaml` declares **no `disk`**. Every deploy destroys all data. |

---

# Demo E2E Remediation

*Added after the demo end-to-end completion phase. This section records what changed for the DEMO only. Production authentication, database and infrastructure work is explicitly NOT part of it.*

## A. Demo-relevant defects FIXED in this phase

| ID | Severity | Fix | File |
|----|----------|-----|------|
| **F-01** | P2 | Invalid `?category=` no longer returns unfiltered data under a false label; it now returns 400 listing the valid categories | `DiscoveryController.parseCategory` |
| **F-04** | P2 | Removed the invalid `catch → flush() → return true` recovery; the unique constraint now propagates and rolls back correctly | `FavouriteService` |
| **D-01** | P2 | TOCTOU genuinely eliminated with a `PESSIMISTIC_WRITE` lock on the buyer row before counting (reuses the existing `UserRepository.findByIdForUpdate`) | `FavouriteService` |
| **A-01** | P2 | Bean-validation errors now return `ApiErrorDto` like every other handler instead of a second JSON shape | `GlobalExceptionHandler` |
| **A-02** | P2 | `instanceof FieldError` guard replaces the unconditional cast, so an object-level constraint can no longer turn a 400 into a 500 | `GlobalExceptionHandler` |

## B. Production-only defects DEFERRED (not demo blockers)

| ID | Deferred because | Needed when |
|----|-----------------|-------------|
| **S-01** | Demo login is intentional and profile-gated; the demo has no real users | Production auth strategy is chosen |
| **S-02** | Swagger/H2-console exposure is acceptable in a demo | Production profile hardening |
| **S-03** | No brute-force risk in a demo with fixed demo numbers | Real credentials exist |
| **S-04 / S-05** | Analytics PII and stack-trace logging are low-impact at demo volume | Production logging/privacy policy |
| **F-02 / F-03** | Name-based kitchen routing and duplicate search APIs work; F-02 is cosmetic for the demo | API consolidation phase |
| **F-05 / A-03 / A-04** | Missing bean validation on `Map` bodies; unguarded `Long.valueOf` | API contract hardening |
| **D-02** | No cascade/orphan cleanup — no deletes occur in the demo | Deletion feature exists |
| **P-01 … P-06** | Production startup, bootstrap, persistence, driver, migrations — all production-infrastructure work | Production phase |

**DEMO BLOCKERS REMAINING: none.**

## C. New tests added

`DemoMarketplaceE2ETest` — 3 tests, **real Spring wiring, real H2 persistence, no mocked services**:

1. `fullMarketplaceEndToEnd` — creates a NEW Area, Society, approved seller, kitchen and two items; verifies discovery, order placement, persisted quantities, stock deduction, and that both seller and buyer see the order.
2. `inventoryQuantityCorrectness` — zero / negative / over-stock rejected with stock unchanged; a valid multi-item order deducts each line correctly and totals correctly.
3. `authorizationBoundaries` — a buyer cannot add offerings to a kitchen, and a buyer outside the covered society cannot order from it.

Test totals: **391 → 394** (+3), all green.

## D. End-to-end result — verified over REAL HTTP

The full marketplace flow was executed against a running application (demo profile, port 8081) using only HTTP calls. Every record was created during the run; none were seeded.

| Step | Result |
|---|---|
| Admin demo login | **200**, role `ADMIN` |
| Admin creates NEW Area | **200** (id created) |
| Admin creates NEW Society under it | **200** |
| Seller demo login | **200**, role `SELLER` |
| Seller creates NEW kitchen with Society-ID coverage | **200** (e.g. id=20) |
| Seller creates item A (stock 10) | **200** |
| Seller creates item B (stock 5) | **200** |
| Buyer demo login | **200**, role `BUYER` |
| Buyer profile set to the new society | **200** |
| Buyer discovers the NEW kitchen | **PASS** via `/api/discovery/kitchens?tab=ALL` |
| Buyer sees the NEW items | **PASS** (2 items) |
| Buyer creates draft (A=2, B=1) | **200** |
| Buyer places order | **200**, `orderNumber=SM-…` |
| Item A stock 10 → **8** | **PASS** |
| Item B stock 5 → **4** | **PASS** |
| Buyer sees own order | **PASS** |
| Seller sees the order | **PASS** |
| Over-stock quantity (999) rejected | **400** |
| Zero quantity rejected | **400** |

## E. Inventory result

The **existing** stock implementation was verified, not replaced:

- Atomic `UPDATE Product SET remainingQuantity = remainingQuantity - :qty` (oversell-safe at the DB level).
- `PESSIMISTIC_WRITE` on the order during placement (`findByIdForUpdate`).
- Validation at draft **and** placement; `consumeStock` aggregates per product before decrementing.
- `Product` correctly separates `remainingQuantity` / `maxQuantity` / `bookedQuantity`.

**No duplicate locking or inventory system was introduced.**

## F. Time-based availability result

The existing `OfferingTiming` rules were exercised and are correct:

- An offering cannot be ordered after today's cutoff — *"Orders Close must not already have passed"* is enforced server-side from the **real clock**.
- "Orders Close must be before Delivery / Ready By" is enforced.
- The E2E test derives its window from `LocalDateTime.now()` (+2h close, +4h delivery) rather than hardcoding a time, so the offering is genuinely orderable at the moment it is created. **No fake current time is used anywhere.**

## G. DEMO COMPLETE vs PRODUCTION READY

> **DEMO COMPLETE.** The marketplace works end-to-end with newly created data over real HTTP.
>
> **NOT PRODUCTION READY.** All four P0 findings remain open: no real authentication (S-01), the `prod` profile cannot start (P-01), no admin bootstrap in `prod` (P-02), and all Render data is ephemeral (P-03). Production authentication, database, migrations, persistent storage and infrastructure remain a separate phase.
All three are independent and must be resolved before production. **P-02 additionally depends on the authentication decision** — under OAuth/IdP a seeded `User` row does not by itself yield a usable admin.