# SocioMart Deep QA Executive Summary

**Generated:** 2026-10-25  
**QA Engagement:** Deep QA, Adversarial Testing & Complete Defect Register  
**Status:** **NO DEEP QA RUN EXECUTED — ONLY PLAN CREATED**

---

## 1. Executive Summary

| Item | Value |
|------|-------|
| **Branch** | `feat/timezone-consistency` |
| **Commit** | `01b1b39 Fix Asia/Kolkata timezone consistency for offering availability` |
| **Working Tree** | **DIRTY** — 8 modified, 10 untracked files |
| **Maven Build** | `mvn clean verify` — **702 passed, 0 failed, 0 errors, 5 skipped** (2:09 min) |
| **PostgreSQL Tests** | 5 skipped (Docker unavailable locally) |
| **Playwright E2E** | 2 passed, 2 failed (port 18082 conflict, trace FS errors) |
| **Deep QA Reports** | **NONE EXIST** — `docs/qa/` directory created now; no prior reports |
| **Confirmed Defects (this run)** | **0** — no adversarial/concurrency/security testing executed |
| **Suspected Defects** | **0** — no testing to generate suspicions |
| **Test Gaps** | All Phases C–L unexecuted |
| **Production Checks** | All unverified — no `prod` profile test, no live Render verification |

### Modified Files (Uncommitted)
- `my-first-spring-api/e2e/marketplace-lifecycle.spec.cjs`
- `my-first-spring-api/e2e/mobile-registration.mobile.spec.cjs`
- `my-first-spring-api/e2e/playwright.config.cjs`
- `my-first-spring-api/e2e/support.cjs`
- `my-first-spring-api/src/main/resources/static/admin.html`
- `my-first-spring-api/src/main/resources/static/js/buyer.js`
- `my-first-spring-api/src/main/resources/static/js/seller.js`
- `my-first-spring-api/src/main/resources/static/seller.html`
- `my-first-spring-api/src/test/java/.../OrderPriceImmutabilityTest.java`

### Untracked Files
- `.design-seller-dashboard-reference.uyer.deployed.js`
- `docs/END_TO_END_VERIFICATION_REPORT.md`
- `docs/MASTER_DEFECT_REGISTER.md`
- `docs/RESPONSIVE_UI_AUDIT.md`
- `my-first-spring-api/README.md`
- `my-first-spring-api/src/test/java/.../UKKitchenBuyerToSellerOrderWorkflowIntegrationTest.java`
- `pr_response.json`
- `tmp.vbs`

---

## 2. Prioritized Defect Register

### 2.1 Confirmed Defects — **EMPTY**
*No testing has been executed in this QA engagement to confirm defects. The `docs/qa/` directory did not exist prior to this summary creation.*

### 2.2 Previously Documented Defects (From Existing Audit Reports)
*These are from `BACKEND_ERROR_AUDIT_REPORT.md` (2026-10-02, commit `3f7931e`) and `MASTER_DEFECT_REGISTER.md` — **not verified by this run**:*

| ID | Severity | Title | Component | Status |
|----|----------|-------|-----------|--------|
| S-01 | P0 | No authentication — mobile = credential | AuthController, SecurityConfig | Documented, not re-verified |
| P-01 | P0 | `prod` profile cannot start against empty DB | application-prod.properties, Flyway | Documented (config analysis) |
| P-02 | P0 | Admin bootstrap deadlock in `prod` | DataInitializer, demo profile | Documented (config analysis) |
| P-03 | P0 | All Render data ephemeral (no disk) | render.yaml, Render free tier | Documented (config analysis) |
| S-02 | P1 | H2 console + Swagger public in all profiles | SecurityConfig | Documented (code review) |
| S-03 | P1 | No rate limiting / lockout | AuthController, AuthRateLimiter | Documented (code review) |
| F-01 | P2 | Invalid category returns all data under false label | DiscoveryController | **Fixed in later PR** |
| F-04 | P2 | Swallowed integrity exception + flush() | FavouriteService | **Fixed in later PR** |
| D-01 | P2 | Favourite max-3 TOCTOU race | FavouriteService | **Fixed in later PR** |
| A-01 | P2 | Two error-body shapes from one advice | GlobalExceptionHandler | **Fixed in later PR** |
| A-02 | P2 | Unconditional `ObjectError`→`FieldError` cast | GlobalExceptionHandler | **Fixed in later PR** |
| PR-01 | P1* | Unbounded `findAll()` in Admin endpoints | AdminService | Risk (not defect) |

*PR-01 is a performance risk, not a confirmed defect.*

### 2.3 Suspected Defects — **EMPTY**

### 2.4 Test Gaps (All Phases C–L)
| Phase | Area | Status | Blocker |
|-------|------|--------|---------|
| C | Business logic state-machine | **NOT RUN** | — |
| D | Security & adversarial testing | **NOT RUN** | — |
| E | Database integrity & concurrency | **NOT RUN** | PostgreSQL Testcontainers need Docker |
| F | Payments, revenue, financial integrity | **NOT RUN** | — |
| G | Timezone, cutoff, scheduler | **NOT RUN** | — |
| H | API contract & negative testing | **NOT RUN** | — |
| I | Frontend, mobile, accessibility | **PARTIAL** (2/4 Playwright, 5/9 viewports) | Port conflict, limited viewport config |
| J | Performance & operational resilience | **NOT RUN** | — |
| K | CI/CD & release safety | **NOT RUN** | — |
| L | Defect reproduction & retesting | **NOT RUN** | — |

### 2.5 Accepted Operational Limitations (Explicit in Documentation)
| Limitation | Source | Impact |
|------------|--------|--------|
| Demo authentication (mobile + password, no OTP) | `README.md` §730 | PII risk if real users |
| No real payment processing | `README.md` §750 | Financial non-goal |
| No GPS/delivery tracking | `README.md` §253 | Delivery non-goal |
| Render free tier cold starts (60-90s) | `README.md` §779 | UX impact |
| No production backup/restore verified | `README.md` §723 | Data loss risk |

---

## 3. Highest-Risk Workflows — Evidence Status

| Workflow | Automated Test Evidence | Live Production Evidence | Status |
|----------|------------------------|-------------------------|--------|
| **1. Buyer/seller authorization & privacy** | `SecurityConfig` role rules, `UserSessionAuthorizationFilter` re-reads DB per request, 702 tests pass | None | **Automated only (H2)** |
| **2. Seller approval & publication restrictions** | `DirectRegistrationSecurityIntegrationTest`, `AdminV1ScopeTest`, `AdminV2HandoverTest` | None | **Automated only (H2)** |
| **3. Order creation & duplicate prevention** | `DemoMarketplaceE2ETest`, `OrderServiceValidationTest`, `OrderPlacementProductionSimulationTest` | None | **Automated only (H2)** |
| **4. Buyer payment claims vs seller-verified payment** | `OrderServicePaymentTest`, `OrderPriceImmutabilityTest` | None | **Automated only (H2)** |
| **5. Cancellation & exactly-once stock restoration** | `InventoryRestorationIntegrationTest` | None | **Automated only (H2)** |
| **6. Revenue reconciliation** | **NO AUTOMATED TEST EXISTS** | None | **UNTESTED** |
| **7. Cutoff times, next-day orders** | `OfferingTimingTest` (25/25), `BusinessTimeZoneTest` (4/4), `OfferingTimingParseTest` (18/18) | None | **Automated only (H2)** |
| **8. Scheduler execution** | `NotificationReminderJob` unit tests only | None | **UNTESTED** (no scheduler wiring test) |
| **9. Mobile checkout & error recovery** | 2/4 Playwright pass, 5/9 viewports untested | None | **PARTIAL** |
| **10. Production config, health, deployment** | `ProductionStartupValidatorTest` (H2 only) | `/api/health` = 503 (historical, 2026-10-16) | **NOT VERIFIED** |

**Critical Distinction:** All passing evidence is from **automated tests against H2 in-memory database**. Zero evidence from PostgreSQL, zero from live Render, zero from real devices.

---

## 4. Final Decision: **NO-GO**

### Reasons
1. **P0 Deployment Blockers Unresolved** — `prod` profile cannot start (P-01), no admin bootstrap (P-02), ephemeral data (P-03). These are architectural, not test gaps.
2. **P0 Authentication Gap** — No real authentication in any profile (S-01). Mobile number = sole credential.
3. **Deep QA Not Executed** — Phases C–L completely unrun. No security, concurrency, revenue, scheduler, or mobile testing.
4. **Critical Test Gaps** — Revenue reconciliation untested, scheduler reliability untested, concurrency untested, 5/9 mobile viewports untested.
5. **CI/CD Unsafe** — `render.yaml` has `autoDeploy: true` (docs say `false`), no Playwright in CI, no dependency scanning.

### Conditions for CONDITIONAL GO
- [ ] `prod` profile starts and persists data on staging PostgreSQL
- [ ] Real authentication implemented and tested
- [ ] All 5 PostgreSQL integration tests pass in CI
- [ ] Playwright full matrix passes (9 viewports × 3 SPAs)
- [ ] Concurrency tests prove stock safety at 25+ concurrent users
- [ ] Revenue reconciliation automated test added and passing
- [ ] `autoDeploy: false` in Render blueprint
- [ ] Dependency scan clean

### Conditions for GO
All CONDITIONAL GO conditions met + full deep QA execution with zero P0/P1 defects.

---

## 5. Top 5 Priority Actions

| Priority | Action | Blocker | Owner |
|----------|--------|---------|-------|
| 1 | Fix `prod` profile startup — Flyway baseline, admin bootstrap, PG driver | Auth strategy decision | Backend |
| 2 | Implement real authentication — replace mobile-only demo login | Product decision | Backend |
| 3 | Run PostgreSQL integration tests — install Docker or use CI | Docker Desktop install | QA/DevOps |
| 4 | Fix Playwright port conflict — debug `reuseExistingServer` | Config fix | QA |
| 5 | Execute concurrency tests — verify stock safety under load | PostgreSQL Testcontainers | QA |

---

## 6. Next Steps

**No code changes, commits, pushes, merges, or deployments have been made.**

To proceed with actual Deep QA execution:
1. Approve the 12-phase test plan (provided in previous response)
2. Resolve blockers: Docker install, staging deployment access
3. Execute Phases C–L systematically and generate all 8 `docs/qa/*.md` reports
4. Final decision will be based on actual evidence

**Awaiting approval to begin Phase C (Business Logic) execution.**