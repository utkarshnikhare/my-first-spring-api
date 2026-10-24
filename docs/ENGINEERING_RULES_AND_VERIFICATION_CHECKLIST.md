# SocioMart — Engineering Rules, Risk Matrix & Verification Checklist

## 1. Engineering Governance & Implementation Principles
1. **Mandatory Loop:** Understand → Implement → Run Normal Tests → Attack Implementation (Adversarial) → Reproduce Defects → Fix Root Causes → Repeat Tests → Review Diff → Update Registers.
2. **Never Trust Unchallenged Code:** Passing standard unit tests is insufficient. Every critical path (especially payment state, authorization, stock, and tenant isolation) must be challenged with adversarial scenarios.
3. **Defense in Depth:**
   - Server-side authoritative validation: Never rely on client-side button hiding or form attributes.
   - Authentication & Session Integrity: Protected endpoints require valid server-side session authentication; logouts must invalidate sessions.
   - Cross-Tenant Authorization (IDOR): No user (Buyer, Seller, or Admin) may read or mutate resources belonging to another entity unless explicitly permitted by role and ownership checks.
4. **Data Integrity & Concurrency:**
   - Database transactions must guard inventory from negative balances.
   - Simultaneous requests for final available units must never oversell.
   - Order cancellation must restore stock idempotently without multiple restorations.
5. **Database & Migration Policy:**
   - Flyway migrations V1 through V13 are strictly authoritative.
   - No speculative migrations, auto-repair initializers, or disabled schema validation.
   - Production configuration must never inherit unsafe demo/test defaults.

---

## 2. Requirements & Risk-Ranked Verification Checklist

| Risk Area | Threat / Adversarial Scenario | Acceptance Criteria | Verification Method |
|---|---|---|---|
| **A. Auth & Sessions** | Reusing expired/invalidated session post-logout; accessing protected endpoints unauthenticated. | HTTP 401/403 returned; session wiped in storage; CSRF token enforced on state-changing requests. | `SecurityAttackSimulationTest.java` |
| **B. Authorization / IDOR** | Buyer A reading/cancelling Buyer B's order; Seller X accessing/modifying Seller Y's kitchen or offering; Buyer calling Admin endpoints. | HTTP 403 Forbidden or 404 Not Found; no cross-tenant data leaked. | `SecurityAttackSimulationTest.java` |
| **C. Input Validation** | Negative/zero price, extreme negative quantity, HTML/XSS script payloads in text fields, SQL injection characters. | Rejected with HTTP 400 or sanitized; server-side constraints reject invalid data. | `SecurityAttackSimulationTest.java` |
| **D. Business Logic** | Buyer attempting to self-mark order as `PAID`; duplicate order submission; double order cancellation restoring stock twice. | Buyer cannot modify payment status; second cancellation is rejected/idempotent; stock only restored once. | `SecurityAttackSimulationTest.java` |
| **E. Concurrency / Last Stock** | 25-50 concurrent buyer threads attempting to purchase the last 1 remaining item. | Exactly 1 buyer successfully orders; 24-49 receive out-of-stock/insufficient inventory; stock never drops below 0. | `ConcurrencyLoadSimulationTest.java` |
| **F. Database & Flyway** | Clean startup from empty schema; validation against existing schema; transaction rollback on mid-request failures. | 13 Flyway migrations validate cleanly; rollback restores database to pre-transaction state. | `PostgresSchemaMigrationTest.java`, Integration tests |

---

## 3. Requirements-to-Evidence Matrix

| Requirement | Implementation | Test | Adversarial Test | Result | Evidence |
|---|---|---|---|---|---|
| Seller Category Synchronization | `User.java`, `Kitchen.java`, `AuthService.java` | `SellerServiceCategoryTest` | Seller category tampering during login | ✅ PASS | 700 Maven / 4 Playwright |
| Homemade Storefront Discovery | `DiscoveryController.java`, `DiscoveryService.java`, `buyer.js` | `SellerKitchenPreviewTest` | Mixed seller product cross-leakage | ✅ PASS | Storefront isolation verified |
| Offering Creation & Presets | `seller.js`, `SellerAppController.java` | `SellerAppScriptStructureTest` | Invalid timing & negative prices | ✅ PASS | Form validation verified |
| Order Detail Drilldown by Date | `OrderItemDetailDto.java`, `seller.js` | `marketplace-lifecycle.spec.cjs` | Querying offering with mismatched session date | ✅ PASS | E2E suite passed in 40.9s |
| Stock Concurrency & Over-ordering | `OrderService.java`, `ProductRepository.java` | `ConcurrencyLoadSimulationTest` | 25-50 concurrent orders on 1 unit | 🔄 Testing | Multi-threaded executor |
| IDOR Defenses (Buyer & Seller) | `BuyerOrderController.java`, `SellerAppController.java` | `SecurityAttackSimulationTest` | Cross-buyer order cancellation & access | 🔄 Testing | MockMvc & API simulation |
| Payment Status Tampering | `BuyerOrderController.java`, `OrderService.java` | `SecurityAttackSimulationTest` | Buyer sending `paymentStatus=PAID` | 🔄 Testing | Immutability asserted |
