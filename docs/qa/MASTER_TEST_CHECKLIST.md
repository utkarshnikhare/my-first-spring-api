# SocioMart Master QA Test Checklist & Inventory

**Document Version:** 1.0.0  
**Last Updated:** 2026-10-26  
**Status:** **IN PROGRESS (PHASE 1–10 EXECUTION)**

---

## 1. Complete Application Feature, Route & Business Rule Inventory

| Item ID | Category | Feature / Route / Endpoint | Expected Business Rule | Verification Method | Status |
|---|---|---|---|---|---|
| **INV-001** | Auth | `POST /api/auth/register-buyer` | Register buyer with mobile, name, society, flat number. Rate-limited (5/15min). | `BuyerRegistrationTest`, Playwright | **PASS** |
| **INV-002** | Auth | `POST /api/auth/register-seller` | Register seller with business type (`KITCHEN`, `HOMEMADE_PRODUCTS`, `BOTH`). Unapproved until admin verifies. | `SellerRegistrationTest`, Playwright | **PASS** |
| **INV-003** | Auth | `POST /api/auth/login` | Mobile + password login; sets session cookie. 20 failed attempts = 429 lock. | `AuthRateLimitSecurityTest` | **PASS** |
| **INV-004** | Auth | `GET /api/auth/session` | Returns current user role, id, profile data, or anonymous state. | Integration Tests | **PASS** |
| **INV-005** | Discovery | `GET /api/discovery/kitchens` | Discovers active, approved meal kitchens in buyer's society. Excludes pure homemade sellers. | `DiscoveryServiceTest` | **PASS** |
| **INV-006** | Discovery | `GET /api/discovery/homemade-stores` | Discovers active, approved homemade product stores. Excludes pure kitchen sellers. | `HomemadeProductsTest` | **PASS** |
| **INV-007** | Discovery | `GET /api/discovery/offerings` | Filter offerings by date, meal type, category. Hide expired/past cutoff items. | `AvailabilityServiceTest`, Timing Tests | **PASS** |
| **INV-008** | Order | `POST /api/buyer/orders` | Atomic order placement, lock stock, snapshot price, validate cutoff window. | `OrderPriceImmutabilityTest`, Concurrency Tests | **PASS** |
| **INV-009** | Order | `GET /api/buyer/orders` | Retrieve authenticated buyer's order history with status tracking. | `BuyerOrderControllerTest` | **PASS** |
| **INV-010** | Order | `POST /api/buyer/orders/{id}/cancel` | Buyer cancels order within allowable window. Stock restored exactly once. | `OrderCancellationTest` | **PASS** |
| **INV-011** | Payment | `POST /api/buyer/orders/{id}/payment-status` | Buyer updates claim to "I've paid" (`BUYER_CLAIMED_PAID`). Never marks `CONFIRMED_PAID` without seller. | `OrderServiceTest` | **PASS** |
| **INV-012** | Seller | `POST /api/seller/products` | Create offering (Kitchen meal or Homemade product). Enforces seller type permission. Supports Draft. | `SellerServiceCreateOfferingValidationTest` | **PASS** |
| **INV-013** | Seller | `PUT /api/seller/products/{id}` | Edit offering. Immutable if active orders exist; updates details cleanly. | `SellerServiceTest` | **PASS** |
| **INV-014** | Seller | `POST /api/seller/products/{id}/pause` | Pause active offering; hides immediately from buyer discovery. | `SellerControllerTest` | **PASS** |
| **INV-015** | Seller | `GET /api/seller/app/dashboard` | Metric cards (revenue, orders, active offerings) filtered by authenticated seller. | `SellerDashboardUiTest` | **PASS** |
| **INV-016** | Seller | `GET /api/seller/app/order-item/{id}` | Order drilldown and customer plate breakdown for specific offering. | `SellerAppServiceTest` | **PASS** |
| **INV-017** | Seller | `POST /api/seller/orders/{id}/delivery-status` | Seller transitions order `PENDING -> ACCEPTED -> READY -> DELIVERED`. | `UKKitchenBuyerToSellerOrderWorkflowIntegrationTest` | **PASS** |
| **INV-018** | Enquiry | `POST /api/enquiries` | Buyer submits inquiry for custom order or product. Status `NEW`. | `EnquiryControllerTest` | **PASS** |
| **INV-019** | Enquiry | `PATCH /api/seller/enquiries/{id}` | Seller updates inquiry status (`CONTACTED`, `CLOSED`). Isolated by seller id. | `EnquiryServiceTest` | **PASS** |
| **INV-020** | Admin | `POST /api/admin/sellers/{id}/approve` | Admin approves pending seller; enables storefront in discovery. | `AdminControllerTest`, Playwright | **PASS** |
| **INV-021** | Admin | `POST /api/admin/sellers/{id}/reject` | Admin rejects seller with documented reason. Disables storefront. | `AdminServiceTest` | **PASS** |
| **INV-022** | Sched | Pre-order Midnight Cutoff | Pre-orders past midnight cutoff transition to closed; future occurrences advance. | `PreorderMidnightDefectReproductionTest` | **PASS** |
| **INV-023** | System | `GET /api/health` | Non-destructive system health probe returning HTTP 200 `UP`. | `HealthControllerTest` | **PASS** |
| **INV-024** | UI/UX | 11 Responsive Viewports | Multi-device responsiveness (320px–1920px) with 0 overflow and >=44px touch targets. | `mobile-registration.mobile.spec.cjs`, Visual Audit | **PASS** |
| **INV-025** | UI/UX | Dark/Light Theme Switching | Consistent high contrast across all elements and modal dialogs. | Theme Inspection, `seller.css` tokens | **PASS** |

---

## 2. Phase Two: Master User Journeys (Journeys A through G)

- **Journey A (Kitchen Seller):** Onboard -> Admin Approve -> Publish Meal -> Buyer Discovery -> Checkout -> Stock Decrement -> Seller Drilldown -> Mark Delivered. (**PASS**)
- **Journey B (Homemade Seller):** Save Draft -> Preview Sheet -> Validate Fields -> Publish Live -> Category Badges -> Edit/Pause -> Replenish Stock. (**PASS**)
- **Journey C (Mixed Seller):** Single Login (`BOTH`) -> Create Kitchen Offering -> Create Homemade Product -> Separate Storefronts -> Shared Dashboard -> Filter by Category. (**PASS**)
- **Journey D (Buyer):** Search & Filter -> View Storefront -> Select Quantity -> Place Order -> Attempt Stale Cart Order (Rejected) -> View Order Tracking. (**PASS**)
- **Journey E (Enquiry):** Submit Custom Product Enquiry -> Validate Required Fields -> Seller Receives Enquiry -> Transition to `CONTACTED` -> Mark `CLOSED`. (**PASS**)
- **Journey F (Admin Governance):** Review Pending Sellers -> Inspect Business Types (`KITCHEN`, `HOMEMADE_PRODUCTS`, `BOTH`) -> Approve -> Suspend Storefront -> Audit Logs. (**PASS**)
- **Journey G (Pre-Order Cutoff):** Set next-day lunch pre-order cutoff at 10:00 PM -> Attempt checkout at 9:59 PM (Succeeds) -> Attempt checkout at 10:01 PM (Rejected 400 Cutoff Passed) -> Next Occurrence calculation verified across midnight. (**PASS**)

---

## 3. Phase Three: Adversarial Security & Attack Vectors

| Attack ID | Vector | Target Endpoint | Payload / Method | Expected Result | Status |
|---|---|---|---|---|---|
| **SEC-001** | Unauthenticated Access | `GET /api/seller/app/dashboard` | No session cookie | 401 Unauthorized / 403 Forbidden | **PASS** |
| **SEC-002** | Cross-Tenant IDOR | `GET /api/seller/app/order-item/{id}` | Seller A requests Seller B's order item | 403 Forbidden (`SellerNotAuthorizedException`) | **PASS** |
| **SEC-003** | Privilege Escalation | `POST /api/admin/sellers/{id}/approve` | Authenticated Buyer or Seller account | 403 Forbidden | **PASS** |
| **SEC-004** | Category Permission Bypass | `POST /api/seller/products` | Kitchen-only seller attempts to post `isHomemade=true` | 400 Bad Request with permission explanation | **PASS** |
| **SEC-005** | Category Permission Bypass | `POST /api/seller/products` | Homemade seller attempts to post Kitchen meal | 400 Bad Request with permission explanation | **PASS** |
| **SEC-006** | Negative Quantity Order | `POST /api/buyer/orders` | `quantity = -5` | 400 Bad Request / Validation Failure | **PASS** |
| **SEC-007** | Zero Quantity Order | `POST /api/buyer/orders` | `quantity = 0` | 400 Bad Request / Validation Failure | **PASS** |
| **SEC-008** | Inverted Cutoff Tampering | `POST /api/buyer/orders` | Expired product ID submitted after cutoff window | 400 Bad Request (`OfferingExpiredException`) | **PASS** |
| **SEC-009** | Price Tampering in Client Payload | `POST /api/buyer/orders` | Tampered price in request body (e.g., ₹1 instead of ₹150) | Ignored; price fetched from DB entity snapshot | **PASS** |
| **SEC-010** | Unconfirmed Payment Forgery | `POST /api/buyer/orders/{id}/payment-status` | Buyer attempts to set `CONFIRMED_PAID` | Rejected / coerced to `BUYER_CLAIMED_PAID` | **PASS** |
| **SEC-011** | CSRF Header Protection | `POST /api/seller/products` | Mutating request with missing or invalid CSRF token | 403 Forbidden (CSRF filter enforced) | **PASS** |
| **SEC-012** | SQL Injection via Search | `GET /api/discovery/kitchens?search=' OR '1'='1` | SQL meta-characters in search query | Sanitized; parameterized JPQL query executed safely | **PASS** |

---

## 4. Phase Four: Concurrency & Multi-User Race Conditions

| Scenario ID | Test Target | Concurrency Level | Verification Target | Status |
|---|---|---|---|---|
| **CON-001** | Last Unit Inventory Race | 10 concurrent buyers | Offering with 5 units remaining; exactly 5 succeed, 5 receive 400 Out of Stock. | **PASS** |
| **CON-002** | Double-Click Order Submission | 2 identical requests | Idempotency token or lock prevents duplicate deduction. | **PASS** |
| **CON-003** | Order Cancellation Race | Simultaneous cancel + delivery | Exactly one terminal state transition allowed. Stock restored once. | **PASS** |
| **CON-004** | Concurrent Dashboards | 25 concurrent reads | Sub-100ms latency, zero DB connection pool exhaustion. | **PASS** |

---

## 5. Phase Five to Seven: Layout, Migrations & Failure Injection

- **UI-001 (Responsive Viewport Overflow):** Automated check of all 11 viewports for `scrollWidth > clientWidth`. Result: 0 overflows. (**PASS**)
- **UI-002 (Touch Targets):** Automated check of touch targets on mobile viewports (<768px). All primary action targets >=44px. (**PASS**)
- **DB-001 (Flyway Schema Integrity):** Clean migration validation V1 through V13. (**PASS**)
- **RES-001 (Database Transaction Rollback):** Simulated runtime exception during order checkout restores inventory atomically. (**PASS**)
