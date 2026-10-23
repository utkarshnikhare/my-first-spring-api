# SocioMart — Project Analysis

> **Purpose of this document.** A read-only analysis of the SocioMart codebase (Spring Boot backend + static
> JavaScript frontend) as it exists in the working tree. It was produced by reading the source, `pom.xml`,
> `render.yaml`, `Dockerfile`, the CI workflow, the migrations and the existing `docs/` notes. **No source code,
> configuration, tests or dependencies were modified to produce this file.**
>
> **Confidentiality rule applied:** this file intentionally contains **no** passwords, secrets or tokens. Environment
> variables and configuration keys are listed **by name only**. Anything that could not be confirmed from the code is
> explicitly marked **_unverified_** rather than guessed.
>
> **Analysis date:** 2026-10-10. **Primary branch analysed:** `copilot/public-health-inventory-tests` (HEAD `db27f10`);
> `main` is at `dc5c7d6`.

---

## 1. Overview

**What it is.** SocioMart is a **society-level (hyper-local) food & homemade-goods marketplace**. It connects home
kitchens and homemade-goods makers (sellers) with neighbours in the same apartment societies (buyers), under the
operational control of an Admin. It is deliberately **location-first**: a buyer only ever sees storefronts that
actually serve their society.

The product is one Spring Boot application that serves **three separate single-page web apps** as static resources
and exposes a REST API under `/api/**`:

| Page | Role | Purpose |
|---|---|---|
| `index.html` | **Buyer** | Location setup, discovery, offering detail, cart/draft, checkout, orders, favourites, enquiries |
| `seller.html` | **Seller** | Dashboard, storefront/offering management, orders, delivery tracking, earnings, templates, quick posts, recurring schedules |
| `admin.html` | **Admin** | Operations console: dashboard, approvals, sellers, buyers, orders, analytics, areas & societies, exports |

**Roles (server-enforced).**

| Role | Main responsibilities |
|---|---|
| **Buyer** | Set society/area, discover eligible storefronts, browse offerings, place orders, track order/payment/delivery status, save favourites, send enquiries |
| **Seller** | Manage storefront(s) and service coverage, publish offerings (one-time + recurring), manage orders, record delivery completion. New sellers start **PENDING** until Admin approval. |
| **Admin** | Operate the marketplace: approve/reject/suspend sellers, manage sellers/buyers/orders, maintain Area/Society master data, monitor analytics & Recorded Order Value, guarded data retention, append-only audit trail. Supports an **Area-scoped** Admin. |
| **Super Admin** | Global scope; additionally creates/manages Admin accounts, configures platform features (free/paid + limits) and per-seller feature grants, manages platform settings. No dedicated Super Admin UI yet — endpoints only. |

**Main features.** Location-first discovery; three apps; Kitchen and Homemade storefronts (`SellerType`); offerings
with quantity limits and per-offering cut-offs; **recurring offerings** with independent per-date occurrences and
overrides; **Seller Dashboard V3** (LIVE/RECURRING views); real order lifecycle with stock reservation; independent
payment and delivery axes; **seller-recorded delivery** (per-order + bulk); Admin operations console with analytics,
CSV exports, retention and audit; ID-backed **Area → Society** master data; feature-flag/entitlement engine.

The project is explicitly a **demonstrable demo**, not a production system (see §8, §9). It is deployed free on Render
using **in-memory H2** that is re-seeded on every boot.

---

## 2. Tech Stack

| Layer | Choice | Version / notes |
|---|---|---|
| Language | Java | **21** (`<java.version>21</java.version>`) |
| Framework | Spring Boot | **4.1.1** (`spring-boot-starter-parent`) |
| Web | `spring-boot-starter-webmvc` | Spring Boot 4 modular starter (note: `webmvc`, not the classic `web`) |
| Security | `spring-boot-starter-security` | Session-based auth, custom session→authority filter, CSRF via cookie |
| Validation | `spring-boot-starter-validation` | Jakarta Bean Validation on DTOs |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate) | JPA entities, `ddl-auto` per profile |
| API docs | `springdoc-openapi-starter-webmvc-ui` | **3.1.0** — Swagger UI + `/v3/api-docs` |
| DB (dev/demo) | H2 (`com.h2database:h2`, runtime) + `spring-boot-h2console` | In-memory `jdbc:h2:mem:sociomartdb` for demo; file-based for `dev`/`prod` |
| DB (target) | PostgreSQL (`org.postgresql:postgresql`, runtime) | Prepared but **not deployed** (no provisioned DB) |
| Migrations | `spring-boot-starter-flyway` + `flyway-database-postgresql` | Two migrations `V1`, `V2`; only enabled on the `postgres-demo` profile |
| Build | Apache Maven via wrapper (`mvnw` / `mvnw.cmd`) | Maven **not required** locally; wrapper included. Spring Boot Maven plugin |
| Test (backend) | `spring-boot-starter-test` + `spring-security-test` | JUnit 5; `spring-security-test` added so the HTTP security layer (roles + CSRF) is actually exercised |
| Test (containers) | `testcontainers-junit-jupiter`, `testcontainers-postgresql` | Native PostgreSQL schema test — **requires Docker** (skipped when unavailable) |
| Test (E2E) | **Playwright** (`@playwright/test` **1.64.0**, CommonJS `.cjs` specs) | Drives the running app in the `demo` profile |
| Frontend | Vanilla HTML/CSS/JS (no framework, no build step) | `index.html`, `seller.html`, `admin.html`; JS modules under `/js`; cache-bust `?v=` query strings |
| CI | **GitHub Actions** — `.github/workflows/ci.yml` ("CI Build Verification") | Push/PR to `main` + manual dispatch; JDK 21 (Temurin), `mvn -B clean verify`, artifact assertions |
| Deployment | **Render** (web service, `runtime: docker`, **free** plan) | `Dockerfile` multi-stage build (Maven→JRE 21); `autoDeploy: false` (manual deploy only) |
| Node tooling | `docs-tools/` (Playwright/Puppeteer-style screenshot & audit scripts, has `node_modules`) | Documentation/verification helpers only, not part of the app build |

**Frontend JS files** (`src/main/resources/static/js`): `config.js` (72 lines, API base resolution), `common.js`
(626, shared fetch/CSRF/UI helpers), `buyer.js` (1461), `seller.js` (3039), `admin.js` (2504), `app.js` (298, buyer
router). CSS: `styles.css` (shared), `admin.css`, `seller.css`.

**Package counts (current tree).** 16 controllers, 23 service classes, 22 repositories, 22 `@Entity` classes + 12
enums in `model`, 33 DTOs, 9 exception classes, 78 backend test classes. (The `docs/BACKEND_ERROR_AUDIT_REPORT.md`
snapshot from 2026-10-02 lists 14 controllers / 26 entities — that predates `HealthController` and
`RecurringScheduleController` and counts some helper classes differently.)

---


## 3. Architecture

### 3.1 Package / folder structure

The Maven project is nested one level down: the real build root is
`my-first-spring-api/my-first-spring-api/` (so `render.yaml`/`Dockerfile`/CI reference `my-first-spring-api/pom.xml`).

```
my-first-spring-api/
├─ pom.xml, mvnw, mvnw.cmd
├─ src/main/java/com/example/my_first_spring_api/
│  ├─ MyFirstSpringApiApplication.java   (@SpringBootApplication, @EnableScheduling)
│  ├─ SecurityConfig.java                (filter chain, roles, CSRF, sessions, demo detection)
│  ├─ DataInitializer.java               (@Profile demo/dev/default; bootstrap accounts + seed)
│  ├─ DemoDataSeeder.java                (idempotent demo seed)
│  ├─ GlobalExceptionHandler.java        (central error mapping)
│  ├─ controller/   (16 @RestController)
│  ├─ service/      (23 classes incl. KitchenVisibility rule engine, OfferingTiming, NotificationReminderJob)
│  ├─ repository/   (22 Spring Data JPA repos)
│  ├─ model/        (22 @Entity + 12 enums)
│  ├─ dto/          (33 DTOs)
│  ├─ security/     UserSessionAuthorizationFilter
│  └─ exception/    (9 exception types)
├─ src/main/resources/
│  ├─ application.properties (+ -demo, -dev, -prod, -postgres-demo)
│  ├─ db/migration/  V1__initial_schema.sql, V2__direct_demo_auth_and_seller_onboarding.sql
│  └─ static/        index.html, seller.html, admin.html, css/, js/
├─ src/test/java/...  (78 test classes; incl. Testcontainers PostgreSQL + Playwright-facing integration)
└─ e2e/               Playwright specs (*.spec.cjs), playwright.config.cjs, support.cjs
```

Top-level repo extras: `render.yaml`, `Dockerfile`, `.github/workflows/ci.yml`, `.env.example`, `Makefile`,
`start.sh` / `start.ps1` / `start.cmd` (one-command runners), `README.md`, `docs/` (status reports), `backups/`
(point-in-time snapshots), `data/` (file-H2 dir), `docs-tools/` (verification scripts). The root also contains many
stray `*.log` / `*.txt` scratch files (build/test output) that are not part of the app.

### 3.2 Controllers → Services → Repositories

Standard three-layer design. Controllers are thin: they resolve the acting user from the `HttpSession`, delegate to a
service, and wrap results in `ResponseEntity`. Business rules live in services; the `KitchenVisibility` class is a
centralised, static eligibility rule engine used by discovery, marketplace and the Admin "visibility diagnostic".
`UserSessionAuthorizationFilter` bridges the app's own session attribute (`BUYER_SESSION_KEY` = user id) to Spring
Security authorities on every request.

---


### 3.3 Security (`SecurityConfig`, roles, CSRF, sessions, demo login)

- **Authentication model.** There is **no real identity provider**. Login is a **demo** mechanism: `AuthController`
  stores the user id in the HTTP session and (on demo profiles) issues a session. `httpBasic`, `formLogin` and the
  default `logout` are all **disabled**; logout is a custom `POST /api/auth/logout` that invalidates the session.
- **Session → authorities.** `UserSessionAuthorizationFilter` (runs before `UsernamePasswordAuthenticationFilter`)
  reads `BUYER_SESSION_KEY`, loads the `User` from the DB **every request** and derives `ROLE_*` authorities, so role
  changes (approval, demotion, blocking) apply on the next request. A `SELLER` who is not approved is projected as
  `SELLER_PENDING`.
- **Authorization rules** (`authorizeHttpRequests`):
  - `/api/superadmin/**` → `hasRole("SUPER_ADMIN")`
  - `/api/admin/**` → `hasAnyRole("ADMIN","SUPER_ADMIN")`
  - `/api/seller/**`, `/api/seller-app/**` → `hasRole("SELLER")`
  - Legacy demo-login endpoints `/api/auth/demo-login` and `/api/seller-app/demo-login` → **`denyAll()`** (retired)
  - Public (`permitAll`): `/api/auth/**`, `/api/health`, `/api/marketplace`, `/api/items`, `/api/search`,
    `/api/kitchens/**`, `/api/products/**`, `/api/discovery/**`, Swagger (`/v3/api-docs/**`, `/swagger-ui/**`),
    `/error`, static pages/assets, `/kitchens`
  - `/h2-console/**` → allowed **only** in a demo environment (`AuthorizationDecision(demo)`), disabled in `prod`
  - Everything else → `authenticated()`
- **CSRF.** Disabled entirely in demo environments. In non-demo, uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`
  (JS-readable cookie) with a `CsrfTokenRequestAttributeHandler` whose deferral is cleared so the cookie is always
  issued; the frontend echoes it back in the `X-XSRF-TOKEN` header (see `common.js` `api()`).
- **Sessions.** `SessionCreationPolicy.IF_REQUIRED`, `sessionFixation().migrateSession()`, `SecurityContextRepository`
  = `HttpSessionSecurityContextRepository`. Cookie `SameSite=Lax` (Strict in `prod`), 30-minute timeout. `AuthController`
  also calls `request.changeSessionId()` on login and clears a stale draft order if the session owner changes.
- **Demo login / registration.** Gated by `SecurityConfig.isDemoEnvironment` (`!prod & (demo|dev|default|postgres-demo)`)
  and `isDirectAuthEnabled` (property `sociomart.demo.direct-auth-enabled` **and** not `prod`). Buyer/Seller
  registration and password login are **only** available on those profiles. Passwords: Buyer/Seller use
  **BCrypt** (`BCryptPasswordEncoder`, capped at 72 UTF-8 bytes); **Admin / Super Admin** passwords are **not** stored
  in the DB — they are compared (constant-time) against configured properties `sociomart.demo.admin-password` /
  `sociomart.demo.super-admin-password` (supplied via env, see §6). Blocked accounts are rejected at login.
- **Bootstrap accounts.** `AdminService.ensureBootstrapAccounts()` creates a Super Admin and an Admin on
  `demo`/`dev`/`default` boot (idempotent). Their login still requires the configured password above.
- **Headers.** `frameOptions.sameOrigin()` (so the H2 console can be framed locally). No CORS is configured —
  same-origin is the supported arrangement.

### 3.4 Frontend pages and JS

- **`index.html` (Buyer)** — bottom nav (Home / Favourites / Orders & Enquiries / Profile), sticky cart bar, hash
  router in `app.js`. Loads `config.js`, `common.js`, `buyer.js`, `app.js`. Theme applied pre-paint from
  `localStorage`.
- **`seller.html` (Seller)** — loads `config.js`, `common.js`, `seller.js`.
- **`admin.html` (Admin)** — 8 primary nav sections (Dashboard, Approvals, Sellers, Buyers, Orders, Analytics,
  Areas & Societies, Exports). Kitchens/Items/Enquiries/Diagnose/Health/Console are intentionally **not** in the nav
  (developer diagnostics / reachable elsewhere) though their endpoints still exist. Loads `config.js`, `common.js`,
  `admin.js`.
- **`config.js`** resolves the API base to **same-origin** by default (`''` → `/api/...`); an explicit
  `window.SOCIO_API_BASE_URL` override is supported for split deployments (and throws on invalid values).
- Static assets are served by the same Spring app; cache-busting is via `?v=` query strings on CSS/JS.

---


## 4. API List

Legend for **Role**: *Public* = no auth (permitAll); *Auth* = any authenticated session; *Seller* = `ROLE_SELLER`
(and, for the seller controllers/services, an **approved** seller); *Admin* = `ADMIN` or `SUPER_ADMIN`;
*SuperAdmin* = `SUPER_ADMIN`. Query parameters are shown where present.

### Auth — `/api/auth` (mostly Public)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/auth/config` | Public | Frontend flags: direct-auth/demo-registration enabled, admin/super-admin login configured |
| GET | `/api/auth/registration-options` | Public | Coverage options for the registration form |
| POST | `/api/auth/register/buyer` | Public (demo only) | Register + log in a buyer (password) |
| POST | `/api/auth/register/seller` | Public (demo only) | Register a seller + create a pending storefront; returns kitchen slug/URL |
| POST | `/api/auth/login` | Public (demo only) | Password login for any role |
| GET | `/api/auth/me` | Auth (session) | Current user profile or "not authenticated" |
| POST | `/api/auth/become-seller` | Auth | Deprecated — always returns 409 with guidance |
| POST | `/api/auth/logout` | Auth | Invalidate session |
| — | `/api/auth/demo-login`, `/api/seller-app/demo-login` | **denyAll** | Retired legacy demo-login endpoints |

### Health
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/health` | Public | Liveness `{"status":"UP"}` |

### Marketplace / Discovery (Public)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/marketplace` | Public | Marketplace home model for the buyer's society |
| GET | `/api/search?q=` | Public | Marketplace search |
| GET | `/api/kitchens` | Public | Active kitchens visible to the buyer |
| GET | `/api/kitchens/{kitchenName}` | Public | Storefront by name |
| GET | `/api/kitchens/{kitchenName}/products` | Public | Offerings for a storefront (by name) |
| GET | `/api/kitchens/id/{id}` | Public | Storefront detail by id (Today / Pre-order split) |
| GET | `/api/products/{productId}` | Public | Offering detail |
| GET | `/api/items` | Public | Global browse: all available menu items |
| GET | `/api/discovery/counts` | Public | Header counts (Live/Tomorrow/Pre-order/All) |
| GET | `/api/discovery/kitchens?tab=` | Public | Discovery cards (`LIVE_NOW`/`TOMORROW`/`PREORDER`/`ALL`) |
| GET | `/api/discovery/categories` | Public | Category tiles with live counts |
| GET | `/api/discovery/items?category=` | Public | Items grouped by dish; invalid category → 400 |
| GET | `/api/discovery/category-kitchens?category=` | Public | Kitchens having items in a category |
| GET | `/api/discovery/search?q=` | Public | Search over dish names |
| GET | `/api/discovery/offers?item=` | Public | Comparison offers for one dish across kitchens |
| GET | `/api/discovery/homemade` | Public | Homemade stores |
| GET | `/kitchens` | Public | `@Controller` redirect → `/#/kitchens` (Buyer SPA route) |

---


### Buyer profile & orders (Auth)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/buyer/profile` | Auth | Buyer profile |
| GET | `/api/buyer/profile/societies` | Auth | Selectable societies |
| GET | `/api/buyer/profile/areas` | Auth | Approved areas + their societies |
| PUT | `/api/buyer/profile` | Auth | Update profile (incl. area/society) |
| POST | `/api/buyer/orders/draft?kitchenId=` | Auth | Create/update a draft (cart) order |
| GET | `/api/buyer/orders/draft` | Auth | Current draft (204 if none) |
| DELETE | `/api/buyer/orders/draft` | Auth | Clear draft |
| POST | `/api/buyer/orders/place` | Auth | Place order (validates profile/coverage/stock, reserves inventory) |
| GET | `/api/buyer/orders/my` | Auth | My orders (active + past) |
| GET | `/api/buyer/orders/{orderId}` | Auth | Order detail |
| POST | `/api/buyer/orders/{orderId}/rate?rating=` | Auth | Rate an order |
| POST | `/api/buyer/orders/{orderId}/reorder` | Auth | Reorder |
| POST | `/api/buyer/orders/{orderId}/cancel` | Auth | Cancel an order |
| PATCH | `/api/buyer/orders/{orderId}/payment-status` | Auth | Buyer updates payment status |

### Favourites / Enquiries / Notifications / Analytics
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/favourites` | Auth | My favourite kitchens |
| POST | `/api/favourites/kitchen/{kitchenId}/toggle` | Auth | Toggle a kitchen favourite |
| GET | `/api/enquiries/my` | Auth (buyer) | My enquiries |
| POST | `/api/enquiries` | Auth (buyer) | Submit an enquiry |
| GET | `/api/enquiries/seller/my` | Seller | Enquiries for my store |
| POST | `/api/enquiries/{enquiryId}/acknowledge` | Seller | Acknowledge an enquiry |
| PATCH | `/api/enquiries/{enquiryId}/status` | Seller | Update enquiry status |
| GET | `/api/notifications` | Auth | Unread notifications |
| PATCH | `/api/notifications/{eventId}/read` | Auth | Mark a notification read |
| POST | `/api/analytics/event` | Public | Record a whitelisted frontend interaction event |

### Seller storefront & offerings — `/api/seller` (Seller, approved)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/seller/kitchen` | Seller | My storefront |
| POST | `/api/seller/kitchen` | Seller | Create a storefront |
| PUT | `/api/seller/kitchen/{kitchenId}` | Seller | Update storefront |
| POST | `/api/seller/kitchen/{kitchenId}/pause` | Seller | Pause storefront |
| POST | `/api/seller/kitchen/{kitchenId}/resume` | Seller | Resume storefront |
| GET | `/api/seller/societies` | Seller | Known societies |
| GET | `/api/seller/coverage-options` | Seller | Area/Society coverage picker (ID-backed) |
| GET | `/api/seller/products` | Seller | My offerings |
| GET | `/api/seller/products/{productId}` | Seller | Offering state for the edit screen |
| POST | `/api/seller/products?kitchenId=` | Seller | Create an offering |
| PUT | `/api/seller/products/{productId}` | Seller | Update offering (protected fields freeze once orders exist) |
| DELETE | `/api/seller/products/{productId}` | Seller | Delete offering |
| GET | `/api/seller/orders` | Seller | My orders |
| GET | `/api/seller/orders/{orderId}` | Seller | Order detail |
| PATCH | `/api/seller/orders/{orderId}/status` | Seller | Update order status |
| PATCH | `/api/seller/orders/{orderId}/payment-status` | Seller | Mark order paid |
| POST | `/api/seller/orders/{orderId}/acknowledge` | Seller | Acknowledge order |

---


### Seller app (Dashboard V3) — `/api/seller-app` (Seller, approved)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/seller-app/dashboard` | Seller | LIVE/RECURRING dashboard model |
| PATCH | `/api/seller-app/products/{productId}/inventory` | Seller | Adjust inventory by delta |
| POST | `/api/seller-app/products/{productId}/sold-out` | Seller | Mark sold out |
| POST | `/api/seller-app/products/{productId}/pause` | Seller | Pause orders |
| POST | `/api/seller-app/products/{productId}/resume` | Seller | Resume orders |
| GET | `/api/seller-app/templates` | Seller | Saved offering templates (max 3) |
| POST | `/api/seller-app/templates` | Seller | Add a template |
| DELETE | `/api/seller-app/templates/{templateId}` | Seller | Delete a template |
| POST | `/api/seller-app/templates/{templateId}/publish` | Seller | Publish a new offering from a template |
| POST | `/api/seller-app/quick-posts` | Seller | Create a today-only quick post |
| GET | `/api/seller-app/quick-posts` | Seller | List quick posts |
| POST | `/api/seller-app/parse-message` | Seller | Parse free-text into structured offering fields |
| GET | `/api/seller-app/history` | Seller | Recent offerings (republish source) |
| POST | `/api/seller-app/batch-republish` | Seller | Batch republish offerings for a date |
| GET | `/api/seller-app/orders/summary?date=` | Seller | Order summary buckets (`all` = no date filter) |
| GET | `/api/seller-app/orders/product/{productId}?date&society&status&delivery` | Seller | Offering drill-down (delivery is a read-only 3rd filter) |
| PATCH | `/api/seller-app/orders/{orderId}/delivery-status` | Seller | Record one order's delivered checkbox |
| POST | `/api/seller-app/orders/product/{productId}/mark-all-delivered?date=` | Seller | Bulk mark delivered (server-computed counts) |
| GET | `/api/seller-app/earnings` | Seller | Seller earnings |

### Recurring schedules — `/api/seller/schedules` (Seller)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/seller/schedules` | Seller | My recurring cards |
| GET | `/api/seller/schedules/{scheduleId}` | Seller | One schedule card |
| GET | `/api/seller/schedules/{scheduleId}/occurrences` | Seller | Resolved per-day rows |
| POST | `/api/seller/schedules/{productId}` | Seller | Create a schedule for an offering |
| PUT | `/api/seller/schedules/{scheduleId}` | Seller | Edit repeating rule / future defaults |
| POST | `/api/seller/schedules/{scheduleId}/end` | Seller | End schedule (history preserved) |
| POST | `/api/seller/schedules/{scheduleId}/extend` | Seller | Extend an ongoing schedule |
| GET | `/api/seller/schedules/occurrences/{occurrenceId}` | Seller | One resolved occurrence |
| PATCH | `/api/seller/schedules/occurrences/{occurrenceId}` | Seller | Per-day override (only that date) |
| POST | `/api/seller/schedules/occurrences/{occurrenceId}/sold-out` | Seller | Per-day Sold-Out toggle |
| POST | `/api/seller/schedules/occurrences/{occurrenceId}/pause` | Seller | Per-day Close-Orders toggle |

---


### Admin console — `/api/admin` (Admin)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/admin/dashboard?date=` | Admin | Dashboard counters for a date window |
| GET | `/api/admin/buyers?search&areaId&societyId` | Admin | Buyer list + search (Area scope from session) |
| GET | `/api/admin/buyers/{id}` | Admin | Buyer detail |
| POST | `/api/admin/buyers/{id}/block` | Admin | Block a buyer (reason) |
| POST | `/api/admin/buyers/{id}/unblock` | Admin | Unblock a buyer (note) |
| POST | `/api/admin/buyers/{id}/support-note` | Admin | Internal support note |
| GET | `/api/admin/sellers?status=` | Admin | Seller list by approval status |
| GET | `/api/admin/sellers/pending` | Admin | Pending sellers |
| GET | `/api/admin/sellers/{id}/detail` | Admin | Seller detail |
| POST | `/api/admin/sellers/{id}/approve` | Admin | Approve seller |
| POST | `/api/admin/sellers/{id}/reject` | Admin | Reject seller (reason) |
| POST | `/api/admin/sellers/{id}/request-changes` | Admin | Request changes (reason) |
| POST | `/api/admin/sellers/{id}/suspend` | Admin | Suspend seller (reason) |
| POST | `/api/admin/sellers/{id}/block` | Admin | Block seller (reason) |
| POST | `/api/admin/sellers/{id}/unblock` | Admin | Unblock seller (note) |
| POST | `/api/admin/sellers/{id}/support-note` | Admin | Seller support note |
| POST | `/api/admin/storefronts/{kitchenId}/pause` | Admin | Pause a storefront |
| POST | `/api/admin/storefronts/{kitchenId}/resume` | Admin | Resume a storefront |
| POST | `/api/admin/storefronts/{kitchenId}/remove` | Admin | Soft-remove a storefront (reason) |
| GET | `/api/admin/kitchens?sellerType=` | Admin | Kitchens list |
| PATCH | `/api/admin/kitchens/{kitchenId}/service-areas` | Admin | Update storefront coverage (ID payload authoritative) |
| GET | `/api/admin/societies` | Admin | Assignable societies |
| GET | `/api/admin/coverage-options` | Admin | Active areas + societies for the coverage editor |
| GET | `/api/admin/locations` | Admin | Full Area→Society master (incl. inactive) |
| POST | `/api/admin/areas` | Admin | Create area |
| PATCH | `/api/admin/areas/{areaId}` | Admin | Rename / enable-disable area |
| POST | `/api/admin/societies` | Admin | Create society |
| PATCH | `/api/admin/societies/{societyId}` | Admin | Rename / enable-disable society |
| GET | `/api/admin/offerings` | Admin | Offerings list |
| GET | `/api/admin/orders?filter&search&date&areaId&societyId&sellerId&buyerId&category&payment&delivery&status` | Admin | Orders monitoring (read-only) |
| GET | `/api/admin/orders/{id}` | Admin | Order detail |
| GET | `/api/admin/enquiries` | Admin | Enquiries list |
| GET | `/api/admin/exports/{domain}.csv?...` | Admin | Filter-aware CSV export (formula-injection safe) |
| GET | `/api/admin/attention` | Admin | "Needs attention" items |
| GET | `/api/admin/diagnostics/visibility?buyerId&kitchenId` | Admin | Why a buyer can/can't see a kitchen (read-only) |
| GET | `/api/admin/system-health` | Admin | Factual runtime status (no invented metrics) |
| GET | `/api/admin/seller-analytics?...` | Admin | Per-seller performance |
| GET | `/api/admin/recorded-order-value?...` | Admin | Recorded Order Value + breakdowns |
| GET | `/api/admin/analytics` | Admin | Analytics summary |
| GET | `/api/admin/traffic?period=` | Admin | Traffic summary |
| GET | `/api/admin/audit-log?limit=` | Admin | Append-only audit log |
| GET | `/api/admin/audit-log/{targetType}/{targetId}` | Admin | Audit entries for one target |
| GET | `/api/admin/retention` | Admin | Retention preview (incl. purge candidate count) |
| POST | `/api/admin/retention` | Admin | Set retention window (days) |
| POST | `/api/admin/retention/purge-enabled` | Admin | Arm/disarm the destructive purge |
| POST | `/api/admin/retention/purge` | Admin | Run purge (guarded: enabled+confirmed+exported+reason) |

---


### Super Admin — `/api/superadmin` (SuperAdmin)
| Method | Path | Role | Purpose |
|---|---|---|---|
| GET | `/api/superadmin/admins` | SuperAdmin | List admin accounts |
| POST | `/api/superadmin/admins` | SuperAdmin | Create an admin account |
| DELETE | `/api/superadmin/admins/{id}` | SuperAdmin | Demote an admin to buyer (Super Admin cannot be demoted) |
| GET | `/api/superadmin/features` | SuperAdmin | Feature flags |
| PUT | `/api/superadmin/features/{featureKey}` | SuperAdmin | Configure a feature (enabled/free-paid/limit) |
| PUT | `/api/superadmin/sellers/{sellerId}/features/{featureKey}` | SuperAdmin | Grant/deny a seller a feature (+limit override) |
| GET | `/api/superadmin/sellers/{sellerId}/features` | SuperAdmin | A seller's feature grants |
| GET | `/api/superadmin/settings` | SuperAdmin | Platform settings |
| PUT | `/api/superadmin/settings/{key}` | SuperAdmin | Set a platform setting |
| GET | `/api/superadmin/analytics` | SuperAdmin | Platform analytics |

### Infrastructure / docs endpoints (permitAll in demo)
`GET /v3/api-docs/**`, `GET /swagger-ui/**`, `GET /swagger-ui.html`, `GET /h2-console/**` (demo only), `GET /error`.

> **Note on Swagger exposure:** the OpenAPI/Swagger UI and `/v3/api-docs` are **publicly accessible** on the demo
> deployment. The README explicitly calls this out as something that must be reviewed before any production use.

---

## 5. Data Model

All entities live in `com.example.my_first_spring_api.model` and use `GenerationType.IDENTITY` ids. The schema is
created by Hibernate `ddl-auto` on H2 profiles and by Flyway (`V1`/`V2`) on the PostgreSQL profile. The two must stay
in sync (a native-Postgres Testcontainers test asserts the V2 schema — see §8).

### Core relationships

- **User** (`users`) — the single identity table for every role (`UserRole`: `BUYER`, `SELLER`, `ADMIN`,
  `SUPER_ADMIN`). Holds `passwordHash` (BCrypt, `@JsonIgnore`), seller onboarding fields, `blocked`, `supportNote`,
  `adminAreaId` (Area-scoped Admin), and `storefrontRemoved`.
  - `User → Society` (`societyRef`, buyer location) and `User → Area` (`areaRef`) — many-to-one, plus denormalised
    `society`/`area` display strings kept for legacy consumers.
  - `User → User` (`approvedBy`) — the Admin who made the last approval decision.
  - Seller lifecycle: `sellerApprovalStatus` (`PENDING`, `APPROVED`, `REJECTED`, `CHANGES_REQUESTED`, `SUSPENDED`),
    `sellerStatusReason`, `approvedAt`. `isApprovedSeller()` treats null status as APPROVED (legacy sellers).
- **Area → Society** — one-to-many (`Society.area`). `Area` is disabled, not deleted; `Society` has a case-insensitive
  `nameKey` and a unique constraint on `(area_id, name_key)`. Both have an `active` flag; disabling never re-points
  references.
- **Kitchen** (`kitchens`, the storefront) — `Kitchen → User` (`seller`, many-to-one). `Kitchen ⇄ Society` is a
  many-to-many join table `kitchen_served_societies` (the **authoritative** service coverage). Also carries a
  denormalised `serviceAreas` string and a `storefrontPaused` (Admin) flag distinct from the seller's `availableToday`.
  `sellerType` ∈ `KITCHEN`, `HOMEMADE_PRODUCTS`, `BOTH` (V2 widened this to include `BOTH`).
- **Product** (`products`, the offering) — `Product → Kitchen` (many-to-one). Rich offering fields: price/unit,
  quantity/remaining, availability date, `ordersPaused`, per-offering `cutoffTime`, `readyByTime`, `category` (CSV),
  pre-order fields (`isPreorder`, `preorderType` = `FIXED`/`FLEXIBLE`, `availableUntilDate`, `timeSlots`),
  `bookedQuantity`.
- **Order** (`orders`) — `Order → User` (`buyer`), `Order → Kitchen` (many-to-one). Three **independent** axes:
  `orderStatus` (`DRAFT`,`ORDERED`,`CONFIRMED`,`READY`,`DELIVERED`,`COMPLETED`,`CANCELLED`), `paymentStatus`
  (`PENDING`,`PAID`,`WILL_PAY_LATER`), and `deliveryStatus` (`NOT_DELIVERED`,`DELIVERED`, seller-recorded). Delivery
  records `deliveredAt` + `deliveryUpdatedBy`; `applyDeliveryStatus(...)` is idempotent and never rewrites the clock.
  `OrderItem` cascades from `Order`.
- **OrderItem** (`order_items`) — `OrderItem → Order`, `OrderItem → Product`, optional `OrderItem → Occurrence`
  (recurring link), plus `scheduledDate`/`scheduledSlot` for pre-orders and a frozen `price`.

---


### Recurring-offerings chain
- **RecurringSchedule** (`recurring_schedules`) — one-to-one with a `Product`; owns `startDate`, `endDate` (Ongoing =
  start + a 90-day internal horizon), `recurrenceDays` (CSV of ISO weekdays), default quantity/close/ready times,
  `status` (`ACTIVE`/`ENDED`), `ongoing`.
- **Occurrence** (`occurrences`) — one stored selling date of a schedule (`Occurrence → RecurringSchedule`), unique on
  `(schedule_id, occurrence_date)`. Per-day quantity/close/ready, `status` (`SCHEDULED`,`LIVE`,`ORDERS_CLOSED`,
  `SOLD_OUT`,`ENDED`), `soldOut`, `ordersPaused`. Each row is independent.
- **OccurrenceOverride** (`occurrence_overrides`) — one-to-one with an `Occurrence`; null fields mean "inherit"; an
  explicit-unlimited quantity is distinguished via `quantityOverridden`.

### Supporting entities
- **Enquiry** (`enquiries`) — `Enquiry → User`, `Enquiry → Kitchen`; `status` (`NEW`,`CONTACTED`,`CLOSED`), message,
  preferred date/quantity, reference image, acknowledgement.
- **Favourite** (`favourites`) — `Favourite → User` plus optional `Kitchen` **or** `Product`; unique on
  `(user_id, kitchen_id)`.
- **SellerTemplate** (`seller_templates`) — `→ User`; a saved offering recipe (max 3/seller); publishing creates a new
  independent `Product`.
- **QuickPost** (`quick_posts`) — `→ Kitchen`; a today-only announcement (not orderable); `requestId` is unique for
  idempotency; `imageData` is a validated data URL.
- **NotificationEvent** (`notification_events`) — per-user title/body, `delivered`/read state.
- **AnalyticsEvent** (`analytics_events`) — append-only traffic/interaction events (`eventType`, optional user/kitchen,
  free-text `detail`). Frontend events are whitelisted in `AnalyticsController`.
- **LedgerEvent** (`ledger_events`) — commercial ledger rows (amount, currency, refs to seller/buyer/enquiry/order).
- **OrderDailyAggregate** (`order_daily_aggregates`) — daily rollup at grain `(day, seller, area, society, category)`
  with `orderCount` + `recordedOrderValue`; upserted by `RetentionService` and deliberately **survives** an order purge.
  Holds no buyer-identifying data.
- **AdminAuditLog** (`admin_audit_logs`) — append-only record of consequential Admin actions (action, actor, target
  type/id/label, old/new state, reason, timestamp). No delete path exists.
- **FeatureFlag** (`feature_flags`) — Super-Admin feature config (`featureKey`, enabled, `pricingType` FREE/PAID,
  `limitValue`). **SellerFeatureGrant** (`seller_feature_grants`) — per-seller grant/deny + `limitOverride`, unique on
  `(seller_id, feature_key)`.
- **PlatformSetting** (`platform_settings`) — generic key/value store for Super-Admin settings.

### Enums (`model`)
`UserRole`, `SellerApprovalStatus`, `SellerType`, `Category` (`BREAKFAST`,`LUNCH`,`DINNER`,`SNACKS`,`SPECIAL`),
`OrderStatus`, `PaymentStatus`, `DeliveryStatus`, `EnquiryStatus`, `PreorderType`, `RecurringScheduleStatus`,
`OccurrenceStatus`, `FeaturePricingType`.

---


## 6. Configuration

### Profiles (`src/main/resources/application*.properties`)
| Profile | Datasource | Seeding | Notable |
|---|---|---|---|
| **default** (none) | H2 **in-memory** `jdbc:h2:mem:sociomartdb` | `DataInitializer` + `DemoDataSeeder` run (`@Profile demo/dev/default`) | Flyway disabled; `ddl-auto=update`; H2 console on |
| **demo** | Inherits in-memory H2 (deliberately **not** overridden) | Seeds on every boot | `direct-auth-enabled=true`; DEBUG logging; CSRF disabled; used by Render + Playwright |
| **dev** | H2 **file** `./data/sociomartdb` | Seeds | verbose SQL logging; `direct-auth-enabled=true` |
| **prod** | H2 **file**, `ddl-auto=validate` | **No** seeding | `direct-auth-enabled=false`; H2 console off; `SameSite=Strict`; WARN logging. **Not production-ready** (see §8/§9) |
| **postgres-demo** | PostgreSQL from env (below) | Flyway **enabled** (`classpath:db/migration`) | `ddl-auto=validate`; H2 console off; prepared migration path, **not deployed** |

Common: `server.port=${PORT:8081}`, `spring.application.name=SocioMart`, Swagger at `/v3/api-docs` +
`/swagger-ui.html`, session cookie `SameSite=Lax` (30-minute timeout). Spring scheduling is enabled
(`@EnableScheduling`) — `NotificationReminderJob` runs.

### Environment variables / configuration keys (names only — no values)
- `PORT` — listen port (Render injects it; falls back to `8081`).
- `SPRING_PROFILES_ACTIVE` — active profile (Render sets `demo`).
- `sociomart.demo.direct-auth-enabled` — master switch for OTP-free demo registration/login (property; set in
  `demo`/`dev`, `false` in `prod`).
- `sociomart.demo.admin-password` — Admin demo password (compared in code; **never stored in DB**). Also supplied as
  the env var `SOCIOMART_DEMO_ADMIN_PASSWORD` (relaxed binding; used by Playwright's `webServer`).
- `sociomart.demo.super-admin-password` — Super Admin demo password (env var `SOCIOMART_DEMO_SUPER_ADMIN_PASSWORD`).
- PostgreSQL profile only: `SOCIOMART_DB_JDBC_URL`, `SOCIOMART_DB_USERNAME`, `SOCIOMART_DB_PASSWORD`.
- Frontend (optional): `window.SOCIO_API_BASE_URL` (a browser global, not a server env var).

`.env.example` only documents `PORT` and states the app runs without it.

### Database & migrations
- `V1__initial_schema.sql` — full schema (all tables, indexes, CHECK constraints on enum-like columns, FKs).
- `V2__direct_demo_auth_and_seller_onboarding.sql` — adds `password_hash` + seller onboarding columns and widens
  `kitchens.seller_type` CHECK to include `BOTH`.

### Render setup (`render.yaml`)
- Single **web** service, name **`sociomart-demo`**, `runtime: docker`, `dockerfilePath: ./Dockerfile`, **`plan: free`**.
- `healthCheckPath: /api/health`. (Note: the `README.md` text says `/api/kitchens`; the actual `render.yaml` uses
  `/api/health` — a doc/code drift; the yaml is authoritative.)
- `envVars`: `SPRING_PROFILES_ACTIVE=demo`. `PORT` is injected by Render (not hard-coded).
- **`autoDeploy: false`** — deliberate: Render must not deploy on push; GitHub Actions CI is the authoritative gate
  and a deploy is an explicit manual action taken after CI is green. **No persistent disk** is declared.
- **Free-plan behaviour:** the service sleeps after ~15 minutes idle; first request after sleep is a ~60–90s cold start.

### Docker (`Dockerfile`)
- Multi-stage: `maven:3.9.6-eclipse-temurin-21` build stage (dependency cache via copying `pom.xml` first, then
  `mvn package -DskipTests`), runtime stage `eclipse-temurin:21-jre`.
- `EXPOSE 8081`; `ENTRYPOINT` runs `java $JAVA_OPTS -jar app.jar`; JVM tuned for small instances
  (`-Xmx256m`, container-aware). Note: the build stage runs `-DskipTests` — tests are **not** run in the Docker/Render
  build (CI is the test gate).

### CI (`.github/workflows/ci.yml` — "CI Build Verification")
- Triggers: push to `main`, PR targeting `main`, and manual `workflow_dispatch`.
- Steps: checkout → set up **JDK 21 (Temurin)** with Maven cache → `mvn -B clean verify` (in `my-first-spring-api`) →
  assert `target/classes` exists **and** a `*.jar` was produced.
- This is the authoritative quality gate; a red CI means the build did not pass regardless of local results.

---


## 7. Change History

A chronological summary reconstructed from `git log` and the merged pull requests (#1 → #21). Dates are the author
dates on `main`/HEAD. The repository has ~220 commits; many intermediate ones are UI polish, cache-bust bumps and
Render "trigger redeploy" commits, so this groups the significant ones. **_unverified_**: exact wall-clock times and a
few very early pre-`main` branch commits are not individually confirmed.

### Foundation (2026-08-29 → 2026-09-01)
- **2026-08-29** Initial Spring Boot API project committed.
- **2026-08-31 → 09-01** Navigation fixes; Swagger + H2 console fixed for Spring Boot 4; first `README`; **Render
  deployment** added (`Dockerfile`, blueprint, configurable `PORT`), then the blueprint/hosting references were removed
  again in a follow-up; multi-role RBAC + **seller approval workflow** + **feature-flag engine** + analytics added;
  order total/stock null-safety fixed; unknown URLs return 404.

### Buyer / Seller / Admin apps take shape (2026-09-01 → 2026-09-04)
- Frontend refactored into a **modular architecture**; loading indicators, cart persistence, navigation refactor.
- Production data initialization with Spring profiles; demo kitchens/items seeded; search + browse endpoints.
- OTP field-name fix; **offering-level category, cutoff, pre-order type/slots, scheduled order items** (model + DTO);
  discovery DTOs and endpoints (category hub, kitchen tabs live/tomorrow/preorder/all, item comparison, storefront by
  id); favourites + enquiries with gated identity actions; order cutoff/slot enforcement and stale-draft recovery.
- **Buyer SPA v1** (all 8 screens, deferred login gate, single-kitchen cart, bottom nav); **Seller SPA v1** (dashboard,
  quick-post parse, favourite templates max 3, publish & batch republish, history, order summary/drill-down, earnings);
  **Admin SPA shell + role gate**.
- One-command runners for all platforms; line-ending policy; README/docs.
- **2026-09-03 → 09-04** Production hardening + bug fixes; **CI** (GitHub Actions) and an E2E delta regression suite
  added; functional/technical PDF docs; demo mode with auto-login and expanded seed data; persistent Render deployment
  (blueprint + Dockerfile) and OTP-remnant cleanup; single Render service on in-memory H2; full responsive overhaul;
  unified warm SocioMart UI theme.

### Buyer ↔ Seller order integration & polish (2026-09-05 → 2026-09-13)
- Internal demo payment flow + My Orders/detail; payment confirmation fixes; deployment report updates; several
  "trigger Render redeploy" commits (later reverted).
- README finalized with verified public demo links and architecture.
- Buyer feedback: kitchen favourites, live filtering, **Will Pay Later**, admin dashboard, demo data; UI/UX polish
  passes on Orders+Enquiries and Admin views.
- **Fix:** `/kitchens` route conflict (public Buyer route vs protected Admin API).
- **2026-09-13** "Merge gap-fix program": notifications, profile verification, analytics.

---


### Delivery + Admin V1 workstream (`delivery-admin-v1`, PRs #1, #4, #5, #6; 2026-10-02 → 2026-10-09)
- **Feature — ID-based Area/Society master + shared `LocationService`**; Manage Areas & Societies screen; Platform
  Console; visibility diagnostic + system health; seller society coverage by ID; coverage no-op save fix.
- **Fix** — Area/Society master CSRF flow; approved Pride World City mapping made orderable; duplicate Sellers-tab
  request; collision-proof order numbers.
- **CI** — enforce a test gate before deployment; **gate Render deployment on verified changes**.
- **Fix** — show remaining seller deliveries in progress.
- **2026-10-09** "Release: Recurring Offerings V2 + Seller Dashboard V3" (PR #7, plus earlier seller dashboard/earnings/
  orders/individual-order UI redesigns on 2026-10-05/06).

### Persistence path + documentation/CI hardening (copilot PRs #8 → #21; 2026-10-09 → 2026-10-10)
| PR | Date | Kind | Summary |
|---|---|---|---|
| #8 | 2026-10-09 | Docs | SocioMart release handover |
| #9 | 2026-10-09 | Docs | Production readiness plan |
| #10 | 2026-10-09 | **Feature** | Prepare zero-cost **PostgreSQL** persistence path (Flyway `V1`/`V2`, `postgres-demo` profile) |
| #11 | 2026-10-09 | Docs | Finalize zero-cost database handover |
| #12 | 2026-10-09 | Docs | Clarify in-memory data export blocker |
| #13 | 2026-10-09 | Docs | Final GitHub/Render status; finalize V2/V3 release acceptance |
| #14 | 2026-10-09 | Docs | Record merged SocioMart release status |
| #15 | 2026-10-09 | Docs | SocioMart release + requirements audit |
| #16 | 2026-10-09 | Docs | Record merged audit and main CI status |
| #17 | 2026-10-10 | Docs | Refresh final main and runtime snapshot |
| #18 | 2026-10-10 | **Feature + Test** | OTP-free **demo registration** + E2E coverage; **native PostgreSQL** schema assertion (Testcontainers) |
| #19 | 2026-10-10 | Docs | Record live Render release verification |
| #20 | 2026-10-10 | Docs | Distinguish runtime vs latest `main` commits |
| #21 | 2026-10-10 | **Test** | Add public **health** and **inventory** regression tests (current HEAD `db27f10`) |

**Delivery model.** Work lands on a feature branch, is reviewed through a PR into `main`, and is merged only once CI is
green. Completed branches are retained (not deleted after merge). PRs #8–#21 are almost entirely documentation/status
reporting plus the two test/feature increments (#10, #18, #21); the substantive product features were delivered earlier
(directly on `main` and via the `delivery-admin-v1` stream).

---


## 8. Current Status

### Done and working (backed by code + the repo's own test/release notes)
- **All three apps** (Buyer, Seller, Admin) are implemented as static SPAs over a REST API.
- **Authentication/authorization**: role-based rules on `/api/admin/**`, `/api/superadmin/**`, `/api/seller/**`,
  `/api/seller-app/**`; session→authority filter; CSRF; OTP-free demo Buyer/Seller registration & password login
  (demo profiles only); pending-seller approval; Area-scoped Admin resolved from the session.
- **Core commerce**: society-scoped discovery, offerings (one-time + **recurring** with per-date occurrences and
  overrides), cart/draft, checkout, order lifecycle, **stock reservation/decrement**, duplicate-submit protection,
  society/service-area enforcement, price immutability.
- **Delivery V2**: per-order delivered checkbox, persistence with `deliveredAt`, uncheck/reset, cancelled exclusion,
  bulk "mark all delivered" (idempotent, server-computed counts), buyer read-only reflection.
- **Seller Dashboard V3**: LIVE/RECURRING views, templates, quick posts, batch republish, earnings, order summary and
  drill-down with society/payment/delivery filters.
- **Admin console V1**: dashboard, approvals, seller/buyer management & block/unblock, order monitoring, analytics,
  Recorded Order Value + breakdowns, Areas & Societies master, filter-aware **CSV exports** (formula-injection
  neutralised), attention items, **append-only audit log**, guarded retention/purge, visibility diagnostic, system health.
- **Tests**: the current README reports **592 tests, 0 failures, 0 errors, 1 skipped** (`.\\mvnw.cmd -B test`, 2026-10-10,
  unmerged feature branch) and a green Playwright run of the 4 desktop/mobile E2E specs. `docs/BACKEND_ERROR_AUDIT_REPORT.md`
  (older snapshot) reported 391/391 passing. CI (`mvn -B clean verify`) is the gate.
- **CI/CD**: GitHub Actions runs the full suite on push/PR to `main`; Render deploys manually (`autoDeploy:false`) with
  the `demo` profile on the free plan; `/api/health` is the health check.
- **PR #21 (current HEAD)** adds public-health-endpoint security coverage and inventory-restoration regression tests
  (e.g. cancelling an order restores stock exactly once; paused/future-preorder offerings restore stock without
  reopening them).

### Partially done / limited
- **Persistence**: `postgres-demo` profile + Flyway migrations are **prepared but not deployed** — no PostgreSQL
  provider/resource is provisioned. The live demo still uses **in-memory H2 re-seeded every boot**, so non-seed data is
  disposable and does not survive a restart/redeploy.
- **Seller Onboarding V1**: flagged **PARTIAL / BLOCKED** in the release notes — OTP-free registration now exists
  (PR #18), but a complete pending-application workflow / reserved-slug guarantees and the auth migration are noted as
  out of scope.
- **Super Admin**: full API surface exists but **no Super Admin UI**.
- **`prod` profile**: present but **not production-ready** (file-H2 + `ddl-auto=validate`, no seeding; see §9).
- **Live/operational acceptance**: several journeys have **not** been verified against the live deployment (below).

### Not yet verified / not tested (explicitly flagged in the repo's own reports)
- **Live end-to-end order placement / buyer occurrence order lifecycle on Render** — intentionally avoided live; only
  local integration tests passed. **unverified** against production.
- **Admin live operations** — the Admin sign-in route loaded, but no authorized Admin session workflow was exercised live.
- **Checkout, cancellation and concurrent purchases**: cancellation and inventory restoration are covered by **unit/
  integration tests** (`InventoryRestorationIntegrationTest`, order-service tests incl. duplicate-submit protection and
  favourites concurrency), but a **live** checkout→payment and **concurrent-purchase/race** scenario is **_unverified_
  against the running deployment** (no live load/concurrency test).
- **The `/api/health` endpoint** now has a dedicated security test (PR #21); its **live** behaviour on Render is
  **_unverified_** in these notes (read-only `/api/kitchens` and `/api/auth/config` were the endpoints reported as
  returning 200 during live verification).
- **Docker/PostgreSQL-gated tests skipped**: the native-PostgreSQL **Testcontainers** schema test is skipped where the
  `docker` executable is unavailable (this was the single skipped test locally). H2 PostgreSQL-mode migration/schema
  validation was run locally instead.

---


## 9. Known Issues, Risks & Technical Debt

The most authoritative internal source is `docs/BACKEND_ERROR_AUDIT_REPORT.md` (2026-10-02, commit `3f7931e`): it
recorded **22 confirmed defects, 7 risks, 12 recommendations**, and states plainly that "nothing in this audit was
fixed — this report is evidence only." Whether later PRs resolved any given item is **_unverified_** here; the items
below are the structural concerns that are still evident in the current code.

### Security concerns
- **No real authentication / demo login.** Identity is a demo mechanism. `/api/auth/**` registration and login are
  `permitAll` (on demo profiles) and roles are effectively assigned by the demo flow; the audit notes "there is no
  authentication, only a demo-login." Not safe for real users.
- **Admin/Super Admin passwords via configuration/env**, compared in code, not stored/rotated as real credentials.
  Anyone with the deployed `demo` env can authenticate as Admin. Password values are never written to this document.
- **Public Swagger/OpenAPI** (`/v3/api-docs`, `/swagger-ui`) is exposed on the demo deployment — the README flags this
  must be reviewed before production.
- **H2 console** is enabled on demo/dev and permitted (in demo) — must never be exposed publicly.
- **CSRF disabled in demo** (acceptable for demo, but a hazard if a demo-style config ever fronts real traffic).
- **Unbounded data access**: the audit found ~28 `findAll()` call sites with pagination on only a few bounded paths —
  a DoS/data-exposure risk as data grows.
- **Secrets in the repo tree**: various stray `*.log`/`*.txt` scratch files and `backups/` snapshots exist at the repo
  root; the Playwright config writes generated Admin/Super-Admin test passwords to an ignored temp file. No real secret
  is hard-coded in the reviewed source, but the working tree is noisy and should be cleaned/gitignored carefully.

### Persistence / production readiness
- **In-memory H2, re-seeded each boot; no persistent disk on Render** — all non-seed data is lost on every
  restart/redeploy. There is **no verified backup/restore** of the live dataset.
- **`prod` profile cannot start cleanly**: `ddl-auto=validate` + `spring.sql.init.mode=never` with no guaranteed schema,
  and `DataInitializer` (the only bootstrap of admin accounts) is `@Profile(demo/dev/default)` — a `prod` deploy would
  start with **zero admins and no way to create one**.
- **PostgreSQL path is prepared, not deployed** — no provider, quota, or backup strategy selected.
- **Docker/Render build runs `-DskipTests`** — tests are only run in CI, not in the image build.

### Technical debt
- **`AdminService` is very large** (multi-responsibility, ~1150+ lines per the audit) — a refactor candidate.
- **Denormalised location strings** (`User.society/area`, `Kitchen.serviceAreas`) are kept alongside the authoritative
  ID-backed `Society`/`Area` references; two representations must be kept consistent.
- **Doc/code drift**: `README.md` says the Render health check is `/api/kitchens` while `render.yaml` uses `/api/health`.
- **Repo hygiene**: many scratch `*.log`/`*.txt` files and `backups/` snapshots committed at the root; the Maven project
  is nested an extra level (`my-first-spring-api/my-first-spring-api`), which complicates tooling.
- **No Super Admin UI**, no external image storage/retention strategy, no operational monitoring, no load/performance
  certification, and payments are **simulated/manual**.

---


## 10. Suggested Next Steps (ranked by priority)

**P0 — Security & safety (do before any real users)**
1. **Lock down the public surface**: restrict/disable Swagger + `/v3/api-docs` and the H2 console on any non-local
   deployment; keep them off in `prod`.
2. **Replace demo auth with a real identity system** (hashed, per-user credentials; proper login for Admin/Super Admin;
   session/token hardening; rate limiting) and gate registration behind a real flow.
3. **Make `prod` actually bootable and safe**: provision schema/migrations for `prod`, seed/bootstrap at least one
   Super Admin out-of-band, and forbid demo-only switches in `prod`.

**P1 — Persistence & data safety**
4. **Stand up a durable database** (the prepared `postgres-demo`/Flyway path) within the free-tier budget; document
   quota and backup limits; attach a Render disk or managed DB so data survives restarts.
5. **Obtain and restore-verify** a complete, owner-approved export of the live H2 dataset; import and diff before any
   cutover.
6. **Bound unbounded queries**: add pagination/limits to the `findAll()`-style endpoints flagged by the audit.

**P2 — Operational confidence**
7. **Verify the not-yet-live-tested journeys** against a real deployment: live checkout→payment, **concurrent purchases
   / stock races** (the code reserves stock and has duplicate-submit protection, but there is no live concurrency test),
   the `/api/health` endpoint on Render, and an authorized Admin operations session.
8. **Run the Docker/PostgreSQL-gated tests in CI** (a Docker-capable runner) so the native-PostgreSQL schema test is no
   longer skipped.
9. Add basic **monitoring/alerting** and a **load/performance** acceptance pass.

**P3 — Product & maintainability**
10. **Finish Seller Onboarding V1** (complete pending-application workflow, reserved-slug guarantees) and build the
    **Super Admin UI** for the existing endpoints.
11. **Refactor `AdminService`** and reconcile the denormalised location strings with the ID-backed master.
12. **Repo hygiene**: clean/gitignore the stray `*.log`/`*.txt` scratch files, decide whether to keep `backups/`, and
    consider flattening the nested Maven directory. Fix the `README`↔`render.yaml` health-check drift.
13. **Image storage/retention** strategy for seller/offering images (currently data-URLs / URLs with no external store).

---

## Summary

**SocioMart** is a Spring Boot **4.1.1 / Java 21** monolith that serves three static single-page apps (Buyer, Seller,
Admin) and a `/api/**` REST API, backed by JPA/Hibernate. It is a **location-first, society-level food & homemade-goods
marketplace** with four server-enforced roles (**Buyer, Seller, Admin, Super Admin**). It is a **demonstrable demo**,
not a production system.

**What works:** the full buyer→seller→admin journey — society-scoped discovery, one-time and **recurring** offerings,
order lifecycle with stock reservation, **seller-recorded delivery**, Seller Dashboard V3, and a substantial Admin
console (approvals, analytics, Recorded Order Value, Areas & Societies master, filter-aware CSV exports, append-only
audit, guarded retention). It is covered by a large automated suite (**~592 tests, 1 Docker-gated skip** per the current
README; an older audit reported 391), a Playwright E2E suite, and a GitHub Actions CI gate (`mvn -B clean verify`).

**Key constraints / risks:** authentication is a **demo mechanism** (no real identity provider); the live deployment
uses **in-memory H2 with no persistent disk**, so non-seed data is lost on every restart; the **PostgreSQL/Flyway**
persistence path is prepared but **not deployed**; the `prod` profile is **not production-ready**; Swagger/H2 are
**publicly exposed**; and live checkout, **concurrent-purchase**, live-Admin and `/api/health` behaviours remain
**_unverified_** against the running service. An internal audit (2026-10-02) logged 22 defects / 7 risks and explicitly
noted none were fixed by that audit.

**Deployment:** Render free plan, Docker runtime, `SPRING_PROFILES_ACTIVE=demo`, `autoDeploy:false` (CI is the gate;
deploys are manual), health check `/api/health` (note: README says `/api/kitchens` — a doc drift).

**Bottom line:** a well-structured, heavily-tested domain layer wrapped in a demo-grade identity/persistence/deployment
shell. The highest-value next steps are **hardening security and exposure**, **standing up a durable database**, and
**verifying the checkout / concurrency / live-Admin journeys** before it could carry real users.

_This analysis modified no source, config, tests or dependencies; the only file created is this document._

