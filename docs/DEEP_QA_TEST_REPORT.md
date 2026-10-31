# SocioMart Deep QA Complete Test Report

**Execution Date:** 2026-10-31  
**Target Git Branch:** `qa/deep-application-audit-and-hardening`  
**Test Authority:** Senior QA Architect, Principal Software Engineer, AppSec Engineer  
**Environments Tested:**
1. Local Test Runtime: Windows, Java 21 (Oracle JDK), Node.js v22.23.2, Playwright 1.64.0, H2 In-Memory (PostgreSQL Mode).
2. Native PostgreSQL Container Runtime: PostgreSQL 16 Alpine (`PostgresNativeMigrationTest`).
3. Production Service: Render Live Web Service (`https://sociomart-demo.onrender.com`), Supabase PostgreSQL.

---

## 1. Overall Test Results Summary

| Test Suite / Harness | Tests Run | Passed | Failed | Errors | Skipped | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Complete Maven Test Suite** | 848 | 841 | 0 | 0 | 7* | **PASS** |
| **New Deep Adversarial & Security Suite** | 11 | 11 | 0 | 0 | 0 | **PASS** |
| **Playwright E2E Browser Suite** | 24 | 24 | 0 | 0 | 0 | **PASS** |
| **Production Read-Only Verification** | 12 | 12 | 0 | 0 | 0 | **PASS** |

*\*Note: 7 Testcontainers tests require a local Docker daemon and are designed with `@Testcontainers(disabledWithoutDocker = true)`. They execute in GitHub Actions CI where Docker is provided.*

---

## 2. Granular Test Suite Breakdown

### 2.1 Backend Integration & Unit Tests (Maven)
- **Execution Command:** `.\mvnw.cmd test`
- **Key Suites Verified:**
  - `DeepAdversarialSecurityAndBoundaryTest`: 11 tests covering zero/negative prices, SQLi/XSS token resilience in instructions, cross-buyer IDOR, cross-seller order IDOR, buyer self-payment blockage, cross-society rejection, unapproved seller dashboard blockage, 20-thread stock contention, seller payment idempotency, rating boundary constraints, and delivery status idempotency.
  - `MultiUserConcurrencyAndAdversarialSecurityTest`: 5 tests covering 10-buyer simultaneous checkout of 5 units, progressive concurrency load stages (1, 5, 10, 25, 50 virtual users), single cancellation stock restoration, and transactional rollbacks.
  - `PostgresSchemaMigrationTest`: 3 tests validating complete Flyway migration lineage (V1 through V21), Hibernate schema parity (26 tables), and payment check constraint compatibility.
  - `PostgresNativeMigrationTest`: Real PostgreSQL 16 regression fixture verifying relation OID constraint ownership (`con.conrelid = tbl.oid`), ensuring same-named constraints on unrelated tables are preserved.
  - `PreorderMidnightDefectReproductionTest` & `MidnightBoundaryAvailabilityRegressionTest`: Verification of DEF-09 order cutoff across IST midnight boundaries.
  - `RecurringFoundationPersistenceTest` & `RecurringBuyerOrderFlowTest`: Complete Wednesday Poha acceptance scenarios, per-occurrence overrides, capacity caps, and schedule edit protections.
  - `SellerDeliveryCompletionServiceTest` & `SellerOrderSummaryBucketsTest`: Seller operational delivery completion, summary counts, and society/payment/delivery composite filtering.

### 2.2 End-to-End Browser Tests (Playwright)
- **Execution Command:** `npx playwright test`
- **Results:** 24 passed across 2 projects (desktop & mobile).
  - `buyer-registration.spec.cjs`: Buyer registers without OTP, duplicate phone is rejected, password login persists.
  - `controlled-clock-workflows.spec.cjs`: DEF-09 cutoff across IST midnight, Orders Open From activation at exact IST minute, legacy start-time activation, and Quick Post IST midnight expiry while accepted orders remain fulfillable.
  - `homemade-storefront.spec.cjs`: Unauthenticated buyer accesses `/homemade` and views homemade storefront via clean slug.
  - `marketplace-lifecycle.spec.cjs`: Complete lifecycle: seller approval -> live offering -> buyer order -> inventory decrement -> seller delivery -> buyer history.
  - `orders-open-from.spec.cjs`: Specific-date scheduled offerings; buyers cannot order before scheduled IST opening time.
  - `quick-post-lifecycle.spec.cjs`: Seller publishes Quick Post; buyers order through existing checkout flow.
  - `seller-onboarding.spec.cjs`: Seller stays pending until Admin approval; cross-role boundary enforcement.
  - `seller-recurring-creation.spec.cjs`: Existing schedules manageable; history accessible.
  - `seller-registration-payment-modes.spec.cjs`: Payment configurations (UPI, GPay, Cash-only, Both) displayed and validated.
  - `visual-qa.spec.cjs`: Responsive audits across 9 viewports for Buyer, Seller, and Admin views; accessibility, form labels, and touch targets.
  - `xss-defense.spec.cjs`: Stored XSS inside `customInstructions` renders escaped in DOM without execution.
  - `mobile-offering-workflows.mobile.spec.cjs` & `mobile-registration.mobile.spec.cjs`: Mobile layout, dialogs, and navigation integrity on mobile viewport.

---

## 3. Production Deployment Smoke Verification

- **Production URL:** `https://sociomart-demo.onrender.com`
- **Verified Commit:** `bbf0eced5f3e7fb2328308360345433cf7cb82e4` (matching GitHub main merge of PR #104).
- **Public Probes:**
  - `GET /api/version`: HTTP 200 OK, reports exact commit SHA `bbf0ece...`.
  - `GET /api/health`: HTTP 200 OK, `{"status":"UP","database":"UP"}`.
  - `GET /api/marketplace`: HTTP 200 OK, returns active kitchens and offerings.
  - `GET /api/discovery/kitchens`: HTTP 200 OK, returns public kitchens (`prishas-kitchen`, `aishkitchen`, `uk-kitchen`, `southindian`, `nagpurkitchen`).
- **Data Protection:** Zero production records were deleted, modified, or truncated. Production database remains pristine.
- **Render Infrastructure:** `render.yaml` line 27: `autoDeploy: false` is strictly preserved.
