# SocioMart — Seller/Offering Detail Assessment & Production-Readiness Report

**Date:** 2026-09-29
**Branch:** `main`
**Scope:** five work areas — order-number consistency, README auth docs, Platform Console,
Seller/Offering detail views, scalability assessment.

Priority scale: **P0** blocker · **P1** high · **P2** medium · **P3** low.
Status: **Closed** = fixed in this change set · **Open** = assessed and deliberately left as-is.

---

## Part A — Phase 4: Seller / Offering detail views (assessed, NOT built)

### Question

The Admin SPA has ten views, but only one detail screen (`adminOrderDetailView`).
Should `adminSellerDetailView` and `adminOfferingDetailView` be added?

### Evidence

| Check | Result |
|---|---|
| Single-resource admin endpoints | Only `GET /api/admin/orders/{id}`. `AdminController` exposes `/sellers`, `/sellers/pending`, `/offerings`, `/kitchens`, `/orders`, `/orders/{id}` — no `/sellers/{id}` or `/offerings/{id}`. |
| `AdminService` detail methods | Only `orderDetail(Long id)`. No `sellerDetail`, `productDetail`, `offeringDetail` or `kitchenDetail`. |
| Dead affordance? | **No.** The seller row is `<div class="seller-row">` with no `data-action`; the offering row likewise. Neither is clickable, so no broken link exists. Actions are the inline `approve-seller` / `open-reject` buttons only. |
| Dangling references | `admin.js` references only `adminOrderDetailView` and `admin-back-orders`. No `admin-seller-detail`, `admin-offering-detail`, or orphaned back-button. |
| Information already on screen | Seller row: name, status pill, mobile, kitchen, area, `live/total` offerings, registered date. Offering row: name, status pill, kitchen, price/unit, available date, cutoff time, quantity. |
| Admin mutations available | Sellers: approve / reject / suspend — all reachable from the list. Offerings: **read-only**; there is no admin offering-mutation endpoint for a detail page to host. |

### Conclusion

**The gap is not real, so it was not built.**

A detail screen earns its place when a record spans more than one screen-width of
related data, or when it hosts actions the list cannot. That describes *orders*
(buyer + kitchen + seller + items + payment + status), which is exactly where the
app already has one. For sellers and offerings:

1. Adding the views would require **new backend endpoints** (`/sellers/{id}`,
   `/offerings/{id}`), new authorization surface and new tests — creating a feature
   rather than filling a gap.
2. Every field a detail page would show is **already in the list row**, and search
   plus status filters already narrow it.
3. There is **no dead affordance** to repair — the rows were never clickable.
4. Offerings are **read-only** for admins, so a detail page would have no actions.

Building them speculatively would add API surface and maintenance cost for no
capability gain. Recorded here as an assessed-and-declined item so the decision is
recoverable rather than silently forgotten.

**Status: Open (P3) — declined by design.** Revisit only if a requirement appears for
"offerings belonging to seller X" or "order history for offering Y"; neither has a
backend endpoint today.

---

## Part B — Phase 5: Classified production-readiness report

### B.1 Closed in this change set

| ID | Finding | Evidence | Impact | Action | Pri |
|---|---|---|---|---|---|
| O-1 | Order numbers collided. The generator was `"SM" + System.nanoTime() % 10000000000L`; that modulus makes the value **cycle every 10 seconds**, so orders ten seconds apart received the same number. | `OrderService.generateOrderNumber()` before this change; `order_number` is `@Column(unique = true, nullable = false)`. | Checkout failed outright on the UNIQUE violation — a buyer-visible, revenue-visible failure. | Replaced with `SM-` + 12 hex chars of `UUID.randomUUID()`, an `OrderRepository.existsByOrderNumber` pre-check, bounded retries (`MAX_ORDER_NUMBER_ATTEMPTS = 5`) and a timestamp fallback. Historical IDs untouched. | **P0 / Closed** |
| C-1 | `createAdmin` accepted any string as a mobile number. The column is UNIQUE but **nullable**, so a bad value produced an unusable admin and a repeat submit hit a constraint-violation 500. | `AdminService.createAdmin` — no validation before this change. | The Platform Console could create broken admin accounts; a duplicate submit produced HTTP 500. | Validates `[6-9]\d{9}` after `trim()` and throws `IllegalArgumentException` → 400 with a readable message. | **P1 / Closed** |
| C-2 | `demoteAdmin` had no last-admin guard. | `AdminService.demoteAdmin` — no count check before this change. | One mis-click could leave the platform with **no administrator account** and no in-app recovery path. | Guard on `userRepository.countByRole(ADMIN) <= 1`, checked before any write. | **P1 / Closed** |
| C-3 | Promoting a SELLER to ADMIN would detach their kitchen and every offering under it. | `AdminService.createAdmin` overwrote the role unconditionally. | Silent data orphaning — the kitchen and its offerings become unreachable from their owner. | SELLER target refused with an explanatory message; SUPER_ADMIN target also refused. | **P1 / Closed** |
| D-1 | README documented behaviour the app does not have: it claimed an admin could be reached "without auth" and that guests could place orders. | `README.md` lines 57 and 59 before this change. | Operators and reviewers were misled about the actual security posture. | Corrected to describe `/api/admin/**` (ADMIN or SUPER_ADMIN) and `/api/superadmin/**` (SUPER_ADMIN), with the demo mobiles. Documentation only — no behaviour change. | **P1 / Closed** |
| P-1 | The Platform Console was a placeholder and did not resolve to a real view. | `admin.js` `adminRoutes['#/console']`. | The highest-privilege capability in the product was unreachable. | `adminConsoleView()` implemented: administrator list with role pills, demote, create-admin, Super Admin guard, and an explicit "Not available" card for feature flags / grants / settings. Cache-busted `admin.css?v=6`, `admin.js?v=5`. | **P1 / Closed** |

### B.2 Verified as already correct (no action needed)

| ID | Control | Evidence | Pri |
|---|---|---|---|
| V-1 | Server-side role enforcement on admin APIs. | `SecurityConfig`: `/api/superadmin/**` → `hasRole("SUPER_ADMIN")`; `/api/admin/**` → `hasAnyRole("ADMIN", "SUPER_ADMIN")`. Verified live: ordinary ADMIN and anonymous callers get 401/403. | — |
| V-2 | Demo login is **not** available in production. | `SecurityConfig.isDemoEnvironment()` = `!prod & (demo \| dev \| default)`; `/api/auth/demo-login` is gated by `new AuthorizationDecision(demo)`, so it is refused under the `prod` profile. | — |
| V-3 | CSRF is enabled outside the demo profiles. | `csrf.disable()` only when `demo`; otherwise `CookieCsrfTokenRepository.withHttpOnlyFalse()`. | — |
| V-4 | Session hardening. | `sessionFixation(migrateSession())`, BCrypt `PasswordEncoder`, `httpBasic`/`formLogin`/`logout` disabled, `same-site=strict` under `prod`. | — |
| V-5 | The remaining Platform Console features are intentionally unexposed. | `SuperAdminController` exposes `/features`, `/sellers/{id}/features`, `/settings` (11 mappings), but there is no agreed rule for valid limits, pricing or who may change them. Surfacing them would publish an undefined policy. | **P2 / Open** |

### B.3 Open risks (assessed, not fixed in this change set)

| ID | Finding | Evidence | Impact | Recommended action | Pri |
|---|---|---|---|---|---|
| A-1 | Production runs on an **embedded H2 file database**; PostgreSQL is commented out. | `application-prod.properties`: `spring.datasource.url=jdbc:h2:file:./data/sociomartdb` with the `postgresql` line commented above it. | Single-process file DB: no horizontal scaling, no concurrent writers across instances, backup story is a file copy, and Render's ephemeral filesystem risks data loss on redeploy. | Move prod to a managed PostgreSQL service (the config stub is already present) and switch `spring.datasource.url` to the environment variable. | **P1 / Open** |
| A-2 | Admin list endpoints load whole tables — **no pagination**. | `AdminService` calls `findAll()` **8 times**; `Pageable` appears in only 3 repositories (`OrderRepository`, `EnquiryRepository`, `AnalyticsEventRepository`) and none is used by the admin list endpoints. | Dashboard, buyers, sellers, kitchens, offerings, orders and enquiries each materialise the full table in memory on every render. Latency and heap growth scale linearly with order count; the admin dashboard degrades as the business grows. | Add paging (or bounded `Top N` queries with a sort) to `/api/admin/*` list endpoints; the repositories already import `Pageable`, so the plumbing exists. | **P1 / Open** |
| A-3 | HTTP sessions are **in-memory container sessions**. | `securityContextRepository` = `HttpSessionSecurityContextRepository`; no `spring.session.*` dependency or store configured. Session timeout is 30m in every profile. | Sessions are lost on every restart/redeploy, and cannot be shared by more than one instance — so the app cannot run with >1 replica without silently logging everyone out. | Move to Spring Session (JDBC/Redis) before any multi-instance deployment. | **P2 / Open** |
| A-4 | Schema is managed with `ddl-auto=update` in the default and dev profiles. | `application.properties` and `application-dev.properties` set `spring.jpa.hibernate.ddl-auto=update`; prod correctly uses `validate`. | Hibernate will mutate schema in place, which cannot express destructive changes and can silently diverge from the intended model. | Use Flyway/Liquibase migrations for every non-demo profile. | **P2 / Open** |
| A-5 | No HikariCP pool tuning. | No `spring.datasource.hikari.*` key in any profile — all defaults. | Defaults sized for a single small app; under load the pool becomes the bottleneck before the database does. | Set explicit `maximum-pool-size` / `connection-timeout` for prod based on measured concurrency. | **P3 / Open** |
| S-1 | Seller / Offering detail views absent. | See Part A. | None today — no dead affordance, all decision fields are already in the list rows. | Declined by design; revisit only with a concrete "list of offerings for seller X" requirement. | **P3 / Open** |

---

## Part C — Scalability assessment (work area 5)

**Verdict: the application is sound for a single-instance demo, and is NOT ready to
scale beyond it.** Three things bind, in this order:

1. **Storage (A-1) is the hard limit.** An embedded H2 file database cannot be shared
   by two application instances. Until prod moves to PostgreSQL, "scaling out" is not
   possible at all — only "scaling up" on one box, which a file DB then caps anyway.
2. **Read model (A-2) is the first thing users will feel.** Admin list endpoints call
   `findAll()` eight times per render. This is cheap with the seeded demo dataset and
   grows linearly with real order volume. It is also the cheapest fix, because
   `Pageable` is already imported in the repositories that need it.
3. **Session state (A-3) is the gate on horizontal scaling.** Even with PostgreSQL in
   place, in-memory `HttpSession` means a second replica cannot serve an existing
   login. Spring Session must land before replicas do.

**Ordering for real scalability:** A-1 (PostgreSQL) → A-2 (pagination) → A-3 (Spring
Session) → A-4 (migrations) → A-5 (pool tuning).

**What already scales acceptably:** the service layer is stateless per request, and
server-side authorization does not depend on UI state. The order-number path now
pre-checks uniqueness rather than attempting a blind insert and rolling back.

**Correction worth recording:** the analytics path is *not* specially optimised — no
repository in the codebase declares `nativeQuery = true`, so dashboard figures are
aggregated in Java from `findAll()` results. That is precisely why A-2 is the first
bottleneck to fix.

---

## Part D — Verification evidence for this change set

| Layer | Result | Evidence |
|---|---|---|
| Unit + integration tests | **308 / 308 pass, 0 failures** (baseline 290 → +18) | `mvnw test` — `BUILD SUCCESS` |
| New: admin safeguards | **14 tests** | `AdminServiceAdminManagementTest` — mobile validation (null/short/long/wrong-prefix/non-numeric), whitespace trimming, SELLER refusal, SUPER_ADMIN refusal, buyer promotion, new-account creation, blank-name fallback, last-admin guard, super/non-admin/unknown-user demotion, and a no-write-on-rejection assertion. |
| New: order numbers | **4 tests** | `OrderNumberUniquenessTest` — `@SpringBootTest` placing real orders through `createOrUpdateDraftOrder` + `placeOrder`: committed format `^SM-[0-9A-F]{12}$`, a 12-order burst staying distinct, persistence/read-back, and the UNIQUE constraint still rejecting a duplicate. |
| Browser E2E (Platform Console) | **33 / 33 pass, 0 uncaught JS errors** | `docs-tools/e2e-console.js` sections A–G: route resolves to `adminConsoleView`; SUPER_ADMIN sees the console; ordinary ADMIN (isolated browser context) has the nav hidden and is refused on direct navigation; client validation short-circuits before the confirmation; cancel creates nothing; confirm creates exactly once; demote cancel/confirm; both safeguards visible; direct URL + refresh; 320–1920 px with no horizontal overflow. |
| Backend E2E (superadmin API) | Round-trip verified | SUPER_ADMIN list/create/demote; duplicate re-submit promotes rather than duplicating; trailing space trimmed; malformed mobiles → 400 with message; SELLER promotion refused; last-admin demote refused; ordinary ADMIN and anonymous → 401/403 on `/api/superadmin/**`. |

### Notable defect found in the test harness itself (not the product)

An earlier revision of `e2e-console.js` reported a spurious 403 from
`/api/superadmin/admins`. Root cause was **the probe**: signing a second page in as an
ordinary ADMIN reused the same cookie jar, silently downgrading the primary SUPER_ADMIN
session. Fixed by running the role-boundary check in an isolated
`browser.createBrowserContext()`. `CONFIG.API_BASE_URL` resolves to `""` (same-origin),
so no cross-origin issue existed. The product code was not at fault.

A second harness defect — a `waitForFunction` that matched on the demoted admin's
absence alone — raced the mid-render empty state and produced four cascading failures.
Fixed by waiting for the list to be *populated and* free of the demoted row.


