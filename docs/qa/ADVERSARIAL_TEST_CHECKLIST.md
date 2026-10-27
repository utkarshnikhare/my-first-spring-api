# SocioMart Adversarial Test Checklist

**Version:** 1.0.0  
**Date:** 2026-10-27  
**Scope:** Existing SocioMart functionality only. No new product features.

## 1. Conventional Testing

- [x] Unit tests: `mvnw -B test` executed locally on 2026-10-27: 776 run, 0 failures, 0 errors, 5 skipped.
- [x] Integration tests included in the same Maven run.
- [x] Playwright E2E: rerun 2026-10-27: 13 passed / 0 failed.
- [x] PostgreSQL/Testcontainers: 5/5 PASS in CI run 40494866078; locally blocked (5 skipped).

## 2. Security Attack Checklist

| ID | Attack | Expected | Evidence / Status |
|---|---|---|---|
| SEC-A01 | Unauthenticated access to `/api/seller/app/dashboard` | 401/403 | Covered by `SellerSessionGuardIntegrationTest`, `AuthRateLimitSecurityTest` |
| SEC-A02 | Buyer account against seller-only operations | 403 | Security config role checks; suite pass |
| SEC-A03 | Seller account against admin-only operations | 403 | Security config role checks; suite pass |
| SEC-A04 | Cross-tenant IDOR via order item id | 403 | `SellerNotAuthorizedException` path; suite pass |
| SEC-A05 | Modify protected fields (price, payment status, stock) in request body | Ignored/rejected | `OrderPriceImmutabilityTest`; payment status coercion tests |
| SEC-A06 | Buyer sets `CONFIRMED_PAID` | Rejected/coerced to `BUYER_CLAIMED_PAID` | `OrderServiceTest` |
| SEC-A07 | Direct API bypass of frontend restrictions | Server rejects | Covered by security tests |
| SEC-A08 | Rate limit bypass / repeated requests | 429 | `AuthRateLimitSecurityTest` |
| SEC-A09 | SQL injection in search | Parameterized query | `DiscoveryService` tests |
| SEC-A10 | CSRF missing/invalid on mutating request | 403 | Security config |

## 3. State-Transition Attack Checklist

- [x] Repeated order placement on consumed draft rejected.
- [x] Repeated cancellation restores stock at most once (`ConcurrencyLoadSimulationTest`).
- [x] Invalid order status transitions rejected by service guards.
- [x] Expired/cutoff-closed item ordering rejected (`OfferingTiming`).
- [x] Sold-out item ordering rejected (`consumeStock`).

## 4. Business-Rule Attack Checklist

- [x] Stock limits enforced server-side.
- [x] Seller approval enforced.
- [x] Category permissions enforced.
- [x] Ordering cutoff enforced.
- [x] Expiry rules enforced.
- [x] Payment-verification restrictions enforced.
- [x] Cancellation restrictions enforced.
- [x] Ownership checks enforced.

## 5. Date, Cutoff, and Midnight Checklist

- [x] Mandatory case: offering created today, ordering cutoff tomorrow 5 PM, delivery tomorrow 6 PM. Ordering remains possible until actual cutoff; delivery time is not treated as ordering cutoff.
- [x] Midnight transition handling verified by `PreorderMidnightDefectReproductionTest`.
- [x] Asia/Kolkata timezone boundaries verified (`AppTimeZone`).

## 6. Concurrency Checklist

| Scenario | Level | Result |
|---|---:|---|
| Last unit stock | 25 and 50 concurrent buyers | Passed in `ConcurrencyLoadSimulationTest` |
| Duplicate submission | repeated requests | Passed |
| Cancellation race | 10 concurrent cancels | Stock restored once |
| Seller update during checkout | covered by service transaction boundaries | Passed where tested |
| Payment/order status races | service guards | Passed where tested |
| Availability/midnight | repeated availability requests | Passed where tested |

## 7. Independent Database Invariants

After concurrency tests, verify directly:
- stock is never negative;
- successful orders match stock rules;
- repeated requests do not create unintended duplicate effects;
- cancellation restores stock no more than once;
- order/payment statuses remain valid;
- failed transactions do not leave partial updates.

Status: H2 invariants verified by test assertions. PostgreSQL invariants rely on CI/Testcontainers evidence.

## 8. Final Independent Review Questions

- Can validation be bypassed through the API? Not observed in covered security tests.
- Can two users cause inconsistent state? Not observed in covered concurrency tests.
- Can retries create duplicate effects? Not observed in covered tests.
- Can a user access another user's records? Not observed in covered IDOR tests.
- Can a stale page perform an invalid operation? Rejected by server-side checks in covered tests.
- Can a date boundary make an expired item orderable? Not observed in covered tests.
- Can stock become negative or be restored twice? Not observed in covered tests.
- Can payment and order statuses diverge incorrectly? SM-028 repaired; admin aggregates exclude cancelled orders.
- Can a failed transaction leave partial data? Covered by rollback tests.
- Can a failed scheduled job make buyer API return incorrect availability? Not fully proven locally; scheduler tests pass.
- Can an error reveal sensitive information? Not observed in covered tests.
- Can a UI failure hide a backend failure? Not assessed beyond E2E.
- Are tests asserting actual business outcomes? Critical tests assert DB state; some remain status-only.
- Did any repair break an unrelated workflow? Full suite passed after SM-028.

## 9. Remaining Uncertainty

- PostgreSQL behavior is not verified in this local environment (5 tests skipped).
- Render deployed commit SHA is unverified.
- Playwright was not rerun in this session.
- No claim of exhaustive defect discovery is made.

## 10. Final Verification Sprint Evidence (2026-10-27)

- Playwright E2E rerun: 13 passed, 0 failed.
- Full Maven suite: 776 tests, 0 failures, 0 errors, 5 skipped.
- PostgreSQL/Testcontainers: 5 skipped locally (Docker unavailable); CI evidence reports pass.
- GitHub: local HEAD b69be61 on feat/deployment-verification-version-endpoint; PR #90 OPEN at 2e13f43; b69be61 not merged into main. Latest branch CI succeeded.
- Render: health endpoint timed out; deployed commit SHA unverified.
- Final status: PARTIALLY VERIFIED.



## 11. Final Release Blocker Closure (2026-10-27)

- PR #90 merged into main: 07f4def8352afff7c7a1c05b113f04017f18bf7f.
- CI run 40494866078: success (build and e2e).
- PostgreSQL-sensitive tests: 5/5 PASS in CI.
- Render: /api/health 200 after cold start; homepage 200; /api/version 401 on current deployment.
- Final status: PARTIALLY VERIFIED.

