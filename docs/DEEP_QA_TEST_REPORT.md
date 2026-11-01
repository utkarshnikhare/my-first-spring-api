# SocioMart Deep QA Complete Test Report

**Execution Date:** 2026-10-31  
**Target Git Branch:** `qa/deep-application-audit-and-hardening`  
**Test Authority:** Senior QA Architect, Principal Software Engineer, AppSec Engineer  
**Environments Tested:**
1. Local Test Runtime: Windows, Java 21 (Oracle JDK), Node.js v22.23.2, Playwright 1.64.0, H2 In-Memory (PostgreSQL Mode).
2. CI Test Runtime: GitHub Actions Ubuntu-latest runner with Docker engine (`build` workflow).
3. Production Service: Render Live Web Service (`https://sociomart-demo.onrender.com`), Supabase PostgreSQL.

---

## 1. Overall Test Results Summary

| Test Suite / Harness | Tests Run | Passed | Failed | Errors | Skipped | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Complete Maven Test Suite (Local)** | 848 | 841 | 0 | 0 | 7* | **PASS** |
| **Complete Maven Test Suite (CI)** | 848 | 848 | 0 | 0 | 0 | **PASS** |
| **Deep Adversarial & HTTP Security Suite** | 11 | 11 | 0 | 0 | 0 | **PASS** |
| **Playwright E2E Browser Suite** | 24 | 24 | 0 | 0 | 0 | **PASS** |
| **Bounded HTTP Load Simulation (10-100 VUs)**| 4 tiers | 4 | 0 | 0 | 0 | **PASS** |
| **Production Read-Only Verification** | 12 | 12 | 0 | 0 | 0 | **PASS** |

### *Explanation of the 7 Skipped Tests in Local Maven Run
The 7 tests that are skipped in local test runs belong to 3 Testcontainers-based test classes:
1. `UserEmailMigrationRegressionTest.java` (3 tests)
2. `PostgresNativeMigrationTest.java` (3 tests)
3. `PostgresRuntimeSmokeTest.java` (1 test)

**Total:** 3 + 3 + 1 = 7 tests.  
**Root Cause:** These classes require a local Docker daemon and are annotated with `@Testcontainers(disabledWithoutDocker = true)`. On local Windows development machines where the Docker desktop daemon is not running, Testcontainers gracefully skips them to prevent compilation/execution blockages.  
**Authoritative CI Verification:** In GitHub Actions CI (Ubuntu runner with active Docker service), all 7 tests execute against real PostgreSQL 16 Alpine containers and pass cleanly:
- GitHub Actions CI Run: `41228529814`
- Job: `build` (Job ID: `124617646950`)
- Result: **Tests run: 848, Failures: 0, Errors: 0, Skipped: 0** (100% test execution rate in CI).

---

## 2. Granular Test Suite Breakdown

### 2.1 Backend Integration & Unit Tests (Maven)
- **Execution Command:** `.\mvnw.cmd test`
- **Key Suites Verified:**
  - `DeepAdversarialSecurityAndBoundaryTest`: 11 tests exercising the real HTTP / API boundary via `MockMvc` and Spring Security filters:
    1. `ADV-01`: Product creation rejects negative or zero prices via `POST /api/seller/products` (HTTP 400 Bad Request).
    2. `ADV-02`: Massive Unicode, Emoji, and SQL injection tokens in `POST /api/buyer/orders/place` persist safely without SQL execution or table corruption (HTTP 200 OK).
    3. `ADV-03`: IDOR attack — Buyer B attempts `GET /api/buyer/orders/{id}` and `POST /api/buyer/orders/{id}/cancel` targeting Buyer A's order (HTTP 404 Not Found, masked).
    4. `ADV-04`: IDOR attack — Seller B attempts `PATCH /api/seller/orders/{id}/payment-status` and `PATCH /api/seller-app/orders/{id}/delivery-status` on Kitchen A's order (HTTP 403 Forbidden).
    5. `ADV-05`: IDOR attack — Buyer attempts `PATCH /api/buyer/orders/{id}/payment-status` with `PAID` (HTTP 400 Bad Request).
    6. `ADV-06`: Cross-society restriction — Buyer from Society South attempts `POST /api/buyer/orders/draft` for Kitchen in Society North (HTTP 400 Bad Request).
    7. `ADV-07`: Unapproved seller attempts `GET /api/seller-app/dashboard` (HTTP 403 Forbidden) and is excluded from `GET /api/discovery/kitchens`.
    8. `ADV-08`: Extreme stock contention — 20 concurrent threads compete for 3 units via real HTTP `MockMvc` requests. Exactly 3 succeed (200 OK), 17 fail (400 Bad Request), stock remains 0 (never negative).
    9. `ADV-09`: Seller mark paid is idempotent (HTTP 200) and rejected on cancelled order (HTTP 400 Bad Request).
    10. `ADV-10`: Rating bounds — rating unpaid order (HTTP 400), rating 0 or 6 (HTTP 400), valid rating 5 (HTTP 200), duplicate rating (HTTP 400).
    11. `ADV-11`: Delivery completion is idempotent (HTTP 200) and preserves `deliveredAt` timestamp across repeated requests.
  - `BoundedLoadSimulationTest`: Evaluates end-to-end HTTP requests over loopback TCP sockets against embedded web server and HikariCP connection pool across 10, 25, 50, and 100 concurrent VUs.
  - `MultiUserConcurrencyAndAdversarialSecurityTest`: 5 tests covering 10-buyer simultaneous checkout of 5 units, progressive concurrency load stages (1, 5, 10, 25, 50 virtual users), single cancellation stock restoration, and transactional rollbacks.
  - `PostgresSchemaMigrationTest`: 3 tests validating complete Flyway migration lineage (V1 through V21), Hibernate schema parity (26 tables), and payment check constraint compatibility.
  - `PostgresNativeMigrationTest`: Real PostgreSQL 16 regression fixture verifying relation OID constraint ownership (`con.conrelid = tbl.oid`), ensuring same-named constraints on unrelated tables are preserved.

### 2.2 End-to-End Browser Tests (Playwright)
- **Execution Command:** `npx playwright test`
- **Results:** 24 passed across desktop & mobile.

---

## 3. Production Deployment Smoke Verification

- **Production URL:** `https://sociomart-demo.onrender.com`
- **Verified Commit:** `bbf0eced5f3e7fb2328308360345433cf7cb82e4` (matching GitHub main merge of PR #104).
- **Public Probes:**
  - `GET /api/version`: HTTP 200 OK, reports exact commit SHA `bbf0ece...`.
  - `GET /api/health`: HTTP 200 OK, `{"status":"UP","database":"UP"}`.
  - `GET /api/marketplace`: HTTP 200 OK, returns active kitchens and offerings.
  - `GET /api/discovery/kitchens`: HTTP 200 OK, returns public kitchens.
- **Data Protection:** Zero production records were deleted, modified, or truncated. Production database remains pristine.
- **Render Infrastructure:** `render.yaml` line 27: `autoDeploy: false` is strictly preserved.
