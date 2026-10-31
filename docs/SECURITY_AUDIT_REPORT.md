# SocioMart Application Security & Penetration Testing Audit Report

**Date of Audit:** 2026-10-31  
**Assessment Team:** Application Security Engineer, Ethical Penetration Tester  
**Target Environment:** Spring Boot 4.1.1, PostgreSQL 16 (Supabase) / Flyway Schema V1-V21, React / PWA Frontend  
**Classification:** Internal Technical Audit & Hardening Attestation  
**Overall Security Posture:** **STRONG (PASS)**

---

## 1. Executive Summary

A comprehensive, adversarial penetration assessment was performed against the SocioMart application architecture. The audit challenged authentication mechanisms, role enforcement, tenant boundaries (IDOR), input handling (SQLi, XSS, Path Traversal), concurrent race conditions, and business state transitions.

All identified attack vectors were thoroughly evaluated against the live system and automated regression tests. The application demonstrates solid defensive engineering, strictly isolating tenants, validating inputs, enforcing session-derived identity, and preventing concurrent inventory corruption.

---

## 2. Threat Vector Evaluation & Findings

### 2.1 Cross-Tenant IDOR (Insecure Direct Object References)
- **Attack Probe:**
  - Buyer B submits order details, cancellation, or payment status requests targeting Buyer A's order ID.
  - Seller B submits delivery status updates, payment verifications, or product pauses targeting Seller A's offerings.
- **Verification Result:** **DEFENDED (PASS)**
  - `OrderService.getOrderDetails` and `cancelOrder` explicitly check `order.getBuyer().getId().equals(buyer.getId())`. Mismatches throw `OrderNotFoundException` (HTTP 404), deliberately withholding confirmation of entity existence.
  - All seller endpoints resolve the authenticated seller from `HttpSession` and verify `kitchen.getSeller().getId().equals(seller.getId())`. Unauthorized attempts fail immediately with `SellerNotAuthorizedException` (HTTP 403).
  - Regression coverage: `DeepAdversarialSecurityAndBoundaryTest.buyerBCannotAccessOrCancelBuyerAOrder`, `sellerBCannotMutateKitchenAOrders`.

### 2.2 Payment State Machine Integrity
- **Attack Probe:**
  - Buyer attempts to mark their own order as `PAID` via `PATCH /api/orders/{orderId}/payment-status`.
  - Buyer attempts to place an order with payment mode not accepted by kitchen (e.g. `cod` when kitchen only takes `GPAY_AND_UPI`).
  - Seller attempts to mark paid an order that was cancelled or belongs to another kitchen.
- **Verification Result:** **DEFENDED (PASS)**
  - Server-side business logic strictly prevents buyers from marking orders `PAID`: `IllegalArgumentException("Payment status is managed by the seller")`.
  - Payment mode validation enforces `SellerPaymentConfiguration.allows(kitchen.getAcceptedPaymentMode(), paymentMethod)`. Mismatches are rejected prior to draft order placement.
  - Seller payment verification transitions (`AWAITING_SELLER_VERIFICATION` -> `SELLER_VERIFIED_PAID`) are idempotent and rejected on cancelled orders.
  - Regression coverage: `DeepAdversarialSecurityAndBoundaryTest.buyerCannotSelfVerifyPaymentToPaid`, `sellerMarkPaidIdempotencyAndCancelledGuard`.

### 2.3 Injection Attacks (SQL Injection, XSS, Path Traversal)
- **Attack Probe:**
  - Injection payloads in `customInstructions`, `deliveryAddress`, search queries, and login parameters:
    - `' OR '1'='1`
    - `'; DROP TABLE orders; --`
    - `<script>alert('xss')</script>`
    - `../../etc/passwd`
    - Massive Unicode, Devanagari text, and Emojis.
- **Verification Result:** **DEFENDED (PASS)**
  - Spring Data JPA parameterization and Hibernate prepared statements neutralize SQL injection vectors; no raw SQL string concatenation exists in dynamic queries.
  - Custom instructions and special delivery notes are stored safely as text and escaped when rendered in buyer and seller web views.
  - E2E Playwright test `xss-defense.spec.cjs` explicitly verifies that stored script tags inside `customInstructions` render escaped and do not execute in browser DOM.
  - Regression coverage: `DeepAdversarialSecurityAndBoundaryTest.orderHandlesMassiveUnicodeEmojiAndSqlTokensSafely`.

### 2.4 Authentication, Session Security & Rate Limiting
- **Attack Probe:**
  - Brute force attempts against password endpoints.
  - Unauthenticated access to private APIs (`/api/seller-app/**`, `/api/admin/**`).
  - Cross-role session contamination (e.g. seller session attempting buyer orders or vice-versa).
- **Verification Result:** **DEFENDED (PASS)**
  - `SecurityConfig` enforces `.anyRequest().authenticated()` for private routes, exposing only documented public routes (`/api/auth/**`, `/api/health`, `/api/version`, `/api/marketplace`, `/api/discovery/**`, `/api/kitchens/**`).
  - `AuthRateLimiter` enforces rate limiting on login and credential endpoints.
  - Sessions are tracked via Spring Session JDBC with HttpOnly cookies.
  - Unapproved sellers (`PENDING`, `REJECTED`, `CHANGES_REQUESTED`) are strictly gated by `requireSeller` and blocked from accessing dashboards or creating offerings.
  - Regression coverage: `AuthRateLimitSecurityTest`, `DirectRegistrationSecurityIntegrationTest`, `SellerSessionGuardIntegrationTest`.

---

## 3. Security Audit Matrix

| Security Domain | Control Tested | Target Component | Result | Telemetry Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **Authentication** | Password hashing (BCrypt) | `AuthService.java` | PASS | DirectRegistrationSecurityIntegrationTest |
| **Authentication** | Credential brute-force limiter | `AuthRateLimiter.java` | PASS | AuthRateLimitSecurityTest |
| **Authorization** | Cross-tenant buyer order IDOR | `OrderService.java` | PASS | DeepAdversarialSecurityAndBoundaryTest #buyerBCannotAccessOrCancelBuyerAOrder |
| **Authorization** | Cross-tenant seller order IDOR | `SellerAppService.java` | PASS | DeepAdversarialSecurityAndBoundaryTest #sellerBCannotMutateKitchenAOrders |
| **Authorization** | Seller approval gate | `SellerAppController.java` | PASS | DeepAdversarialSecurityAndBoundaryTest #unapprovedSellerBlockedFromOperationsAndDiscovery |
| **Data Integrity** | Atomic inventory decrement | `ProductRepository.java` | PASS | MultiUserConcurrencyAndAdversarialSecurityTest #concurrentPurchasesCannotOversellStock |
| **Data Integrity** | Extreme stock contention (20 threads) | `OrderService.java` | PASS | DeepAdversarialSecurityAndBoundaryTest #extremeStockContentionNeverOversells |
| **Input Validation** | Non-negative/non-zero price check | `SellerService.java` | PASS | DeepAdversarialSecurityAndBoundaryTest #productCreationRejectsNegativeOrZeroPrice |
| **Injection Defense**| SQLi & XSS payload resilience | `Order.java` / JPA | PASS | DeepAdversarialSecurityAndBoundaryTest #orderHandlesMassiveUnicodeEmojiAndSqlTokensSafely |
| **XSS Defense** | Stored script escaping in DOM | `static/js/buyer.js` | PASS | Playwright E2E: xss-defense.spec.cjs |
| **Database Migrations**| OID ownership constraint isolation | Flyway `V20` Java Migration | PASS | PostgresNativeMigrationTest |

---

## 4. Certification

The SocioMart application layer exhibits rigorous security controls with zero critical vulnerabilities detected. All endpoints correctly enforce authentication, role permissions, and tenant boundaries.
