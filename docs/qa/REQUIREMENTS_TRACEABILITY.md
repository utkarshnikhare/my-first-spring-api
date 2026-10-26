# SocioMart Comprehensive Requirements Traceability Matrix

**Matrix Version:** 1.0.0  
**Effective Date:** 2026-10-26  
**Status Key:**
- `VERIFIED`: Tested successfully against authoritative acceptance criteria with recorded automated evidence.
- `IMPLEMENTED — UNVERIFIED`: Code implemented but missing dedicated regression evidence.
- `PARTIALLY IMPLEMENTED`: Functional gap identified against specification.
- `MISSING`: Requirement not yet implemented.
- `TEST FAILED`: Regression or automated test failing.
- `BLOCKED`: Blocked by external technical or platform dependencies.

---

## 1. Traceability Ledger

| Req ID | Source Doc & Section | Requirement Description | Role & Scope | Implementation Location | Verification Test / Evidence | Status |
|---|---|---|---|---|---|---|
| **HP-01** | DOC-01 §1, §3 | Single seller account supports Kitchen, Homemade, or Both without requiring duplicate accounts. | Seller / Core Auth | `User.java`, `Kitchen.java`, `SellerType.java` | `HomemadeProductsTest.java` (PASS) | **VERIFIED** |
| **HP-02** | DOC-01 §4, §23 | Business type is persisted as structured data on the seller/kitchen entity. | Backend / Schema | `V1__initial_schema.sql`, `Kitchen.java` (`sellerType`) | `SellerServiceCategoryTest.java` (PASS) | **VERIFIED** |
| **HP-03** | DOC-01 §2, §14 | Separate Buyer Home discovery tiles for Food & Kitchens vs Homemade Products (no cross-contamination). | Buyer Discovery | `DiscoveryService.java`, `buyer.js` (`renderHome()`) | `marketplace-lifecycle.spec.cjs`, `HomemadeProductsTest.java` (PASS) | **VERIFIED** |
| **HP-04** | DOC-01 §4, §23 | Seller registration allows selecting category: Kitchen, Homemade, or Both. | Seller Onboarding | `seller.html`, `seller.js` (`sellerRegistrationSubmit()`) | `mobile-registration.mobile.spec.cjs` (PASS) | **VERIFIED** |
| **HP-05** | DOC-01 §5 | Admin seller approval displays requested business type and enables approval. | Admin Governance | `AdminController.java`, `AdminService.java` | `admin-smoke.spec.cjs` (PASS) | **VERIFIED** |
| **HP-06** | DOC-01 §6, §23 | Sellers can request business type change; disabling category preserves existing history non-destructively. | Seller / Admin | `SellerService.java`, `Kitchen.java` | `SellerServiceUpdateKitchenTest.java` (PASS) | **VERIFIED** |
| **HP-07** | DOC-01 §7 | Single unified seller dashboard with combined earnings and order statistics. | Seller App | `SellerAppService.java`, `seller.js` (`sellerHomeView()`) | `SellerDashboardUiTest.java` (PASS) | **VERIFIED** |
| **HP-08** | DOC-01 §8 | Shared My Offerings screen with category filters: All, Kitchen, Homemade. | Seller App | `seller.js` (`sellerHistoryView()`), `Product.java` | `SellerFilterScriptStructureTest.java` (PASS) | **VERIFIED** |
| **HP-09** | DOC-01 §9, §22 | Add Offering entry point: single-category opens form directly; Both prompts category choice. | Seller App | `seller.js` (`sellerAddView()`) | `marketplace-lifecycle.spec.cjs` (PASS) | **VERIFIED** |
| **HP-10** | DOC-01 §10 | Homemade product attributes: name, category, price, unit, photos, description, ingredients, shelf life. | Seller / Offering | `Product.java`, `ProductCreateDto.java`, `seller.js` | `SellerServiceCreateOfferingValidationTest.java` (PASS) | **VERIFIED** |
| **HP-11** | DOC-01 §11 | Availability models: Always Available, Made on Request (min prep time), Specific Date (cutoff & delivery). | Offering Timing | `Product.java`, `OfferingTiming.java` | `PreorderMidnightDefectReproductionTest.java` (PASS) | **VERIFIED** |
| **HP-12** | DOC-01 §12 | Stock management: Limited Quantity vs No Limit; finite stock cannot oversell under concurrency. | Inventory / Order | `ProductRepository.java` (`consumeStock`), `OrderService.java` | `MultiUserConcurrencyAndAdversarialSecurityTest.java` (PASS) | **VERIFIED** |
| **HP-13** | DOC-01 §13 | Fulfilment options: Pickup, Delivery, Society Delivery supported. | Fulfilment | `Product.java`, `Order.java`, `buyer.js` | `buyer-smoke.spec.cjs` (PASS) | **VERIFIED** |
| **HP-14** | DOC-01 §14 | Buyer discovery for Homemade products with society coverage and search. | Buyer Discovery | `DiscoveryService.java`, `buyer.js` | `kitchen-storefront.spec.cjs` (PASS) | **VERIFIED** |
| **HP-15** | DOC-01 §15 | Separate storefronts for Kitchen (`kitchen.html`) and Homemade; logged-out URL access supported. | Storefronts | `KitchenVisibility.java`, `buyer.js` (`renderStorefront()`) | `kitchen-storefront.spec.cjs` (PASS) | **VERIFIED** |
| **HP-16** | DOC-01 §16 | Ordering flow reuses existing order engine, payment options, and delivery address logic. | Buyer Checkout | `OrderService.java`, `buyer.js` | `buyer-smoke.spec.cjs` (PASS) | **VERIFIED** |
| **HP-17** | DOC-01 §17 | Send Enquiry flow for made-to-order products; seller notification and enquiry tracking. | Enquiries | `EnquiryController.java`, `EnquiryService.java`, `seller.js` | `enquiry-workflow.spec.cjs` (PASS) | **VERIFIED** |
| **HP-18** | DOC-01 §18 | Configurable enquiry lead fee recorded in ledger service only upon successful submission. | Monetization | `EnquiryService.java` (`resolveLeadFee()`), `LedgerService.java` | `EnquiryServiceTest.java` (PASS) | **VERIFIED** |
| **HP-19** | DOC-01 §19 | Homemade orders appear in seller order list with distinct badges and status transitions. | Seller Orders | `SellerAppService.java`, `seller.js` (`sellerOrdersView()`) | `seller-smoke.spec.cjs` (PASS) | **VERIFIED** |
| **HP-20** | DOC-01 §20 | Notifications: Order placed, enquiry received, delivery ready via NotificationService. | Notifications | `NotificationService.java`, `NotificationReminderJob.java` | `SellerNotificationTest.java` (PASS) | **VERIFIED** |
| **HP-21** | DOC-01 §21 | Location & society model: Shared societies and delivery area infrastructure. | Shared Areas | `AreaRepository.java`, `SocietyRepository.java` | `SellerServiceAreaTest.java` (PASS) | **VERIFIED** |
| **HP-22** | DOC-01 §22 | Product publishing lifecycle: Save Draft, Preview sheet, Publish, Pause, Resume, Share Link. | Lifecycle | `Product.java`, `seller.js` (`saveDraft`, `previewOffering`) | `marketplace-lifecycle.spec.cjs` (PASS) | **VERIFIED** |
| **HP-23** | DOC-01 §23 | Backend enforcement of category permissions: Kitchen-only cannot publish Homemade; Homemade-only cannot publish Kitchen. | Security / Rules | `SellerService.java` (`validateSellerCategoryPermission`) | `SellerServiceCreateOfferingValidationTest.java` (PASS) | **VERIFIED** |
| **OPS-01** | DOC-02 §1 | Independent Order Status (`CONFIRMED`, `DELIVERED`, `CANCELLED`) & Payment Status (`AWAITING_SELLER_VERIFICATION`, `UNPAID`, `PAID`). | Order Lifecycle | `Order.java`, `OrderStatus.java`, `PaymentStatus.java` | `OrderServicePaymentTest.java` (PASS) | **VERIFIED** |
| **OPS-02** | DOC-02 §2 | Buyer checkout options: "I've paid" sets payment pending verification; "I'll pay later" sets UNPAID. | Buyer Checkout | `OrderService.java` (`createOrder`), `buyer.js` | `OrderServicePaymentTest.java` (PASS) | **VERIFIED** |
| **OPS-03** | DOC-02 §3 | Seller actions: Mark Payment Received sets `PAID` without changing order status; Mark Delivered sets `DELIVERED` without changing payment status. | Seller Order Action | `OrderService.java` (`markOrderAsPaid`, `updateOrderStatus`) | `OrderServicePaymentTest.java`, `seller-smoke.spec.cjs` (PASS) | **VERIFIED** |
| **OPS-04** | DOC-02 §3 | Seller cancellation requires reason, records actor/timestamp, prevents invalid transitions (e.g. `DELIVERED` -> `CANCELLED`), no automated refund claim. | Seller Order Action | `OrderService.java` (`cancelOrderLocked`) | `OrderServicePaymentTest.java` (PASS) | **VERIFIED** |
| **OPS-05** | DOC-02 §4 | Combined display labels: `Confirmed · Buyer says paid`, `Confirmed · Pay later`, `Confirmed · Paid`, `Delivered · Paid`, `Delivered · Payment pending`, `Cancelled`. | DTO / Presentation | `OrderDto.java` (`getDisplayStatus()`), `seller.js` | `OrderDtoTest.java` (PASS) | **VERIFIED** |
| **OPS-06** | DOC-02 §5 | Concurrency protection: Pessimistic locking (`findByIdForUpdate`) prevents silent overwrites during status updates. | Data Integrity | `OrderRepository.java` (`findByIdForUpdate`), `OrderService.java` | `MultiUserConcurrencyAndAdversarialSecurityTest.java` (PASS) | **VERIFIED** |
| **EXP-01** | DOC-03 §2 | Past-date items excluded from buyer discovery shortcuts and counts. | Buyer Discovery | `DiscoveryService.java`, `AvailabilityService.java` | `BuyerExpiredItemsTest.java` (PASS) | **VERIFIED** |
| **EXP-02** | DOC-03 §3 | Same-day sold-out and cutoff-closed items excluded from available counts and rejected at checkout. | Buyer Discovery | `AvailabilityService.java` (`assertOrderable`) | `PreorderMidnightDefectReproductionTest.java` (PASS) | **VERIFIED** |
| **EXP-03** | DOC-03 §6 | Idempotent midnight background refresh job (`MidnightExpiryJob`) marks past occurrences `ENDED` without deleting rows. | Background Jobs | `MidnightExpiryJob.java` (`@Scheduled(cron="0 0 0 * * ?")`) | `MidnightExpiryJobTest.java` (PASS) | **VERIFIED** |
| **EXP-04** | DOC-03 §7 | Query-time availability calculation pinned to `Asia/Kolkata` ensures availability is correct even if job is delayed. | Shared Availability | `AppTimeZone.ZONE`, `AvailabilityService.java` | `PreorderMidnightDefectReproductionTest.java` (PASS) | **VERIFIED** |
| **EXP-05** | DOC-03 §8 | Historical occurrences, orders, and templates are never deleted on expiry. | Data Retention | `MidnightExpiryJob.java`, `OrderService.java` | `MidnightExpiryJobTest.java` (PASS) | **VERIFIED** |
| **PRE-01** | DOC-04 §1–4 | Pre-order configured today with cutoff tomorrow at 5:00 PM remains orderable across midnight until 5:00 PM tomorrow. | Core Pre-order | `OfferingTiming.java` (`isPastCutoff`, `resolveCutoffDateTime`) | `PreorderMidnightDefectReproductionTest.java` (Test A - PASS) | **VERIFIED** |
| **PRE-02** | DOC-04 §6 | Ordering rejected once cutoff passes (e.g. 5:01 PM) across discovery, detail, cart, and checkout. | Checkout Validation | `OfferingTiming.java` (`enforceOrderWindow`), `AvailabilityService.java` | `PreorderMidnightDefectReproductionTest.java` (Test B - PASS) | **VERIFIED** |
| **PRE-03** | DOC-04 §7 | Asia/Kolkata timezone pinned with minute precision; delivery time distinct from cutoff. | Date-Time Boundary | `AppTimeZone.ZONE`, `OfferingTiming.java` | `PreorderMidnightDefectReproductionTest.java` (Test C, G - PASS) | **VERIFIED** |
| **PRE-04** | DOC-04 §7 | Acceptance Tests A through H fully automated and passing deterministically with synthetic clock. | Regression Suite | `PreorderMidnightDefectReproductionTest.java` | `mvn test -Dtest=PreorderMidnightDefectReproductionTest` (8/8 PASS) | **VERIFIED** |
| **DEF-01** | DOC-05 | Registration rate limiting quota accounting strictly post-validation; proxy IP headers parsed (`CF-Connecting-IP`, `X-Real-IP`). | Auth Security | `AuthController.java`, `AuthRateLimiter.java`, `seller.js` | `AuthRateLimitSecurityTest.java` (7/7 PASS) | **VERIFIED** |
| **DEF-02** | DOC-05 | Unauthenticated bottom navigation hidden on `seller.html` until session is verified. | UI / Navigation | `seller.html`, `seller.js` (`sellerRender()`) | `visual-qa.spec.cjs`, `seller-smoke.spec.cjs` (PASS) | **VERIFIED** |
| **DEF-03** | DOC-05 | Natural mobile scrolling on seller registration (removed nested scroll container). | Mobile UI/UX | `seller.css` (`.seller-registration-card`) | `visual-qa.spec.cjs` (PASS) | **VERIFIED** |
| **DEF-04** | DOC-06 | Seller dashboard layout anomaly resolved (.sd-offerings-grid, header CTA, isolated earnings card). | Responsive UI | `seller.css`, `seller.js` (`sellerHomeView()`) | `SellerDashboardUiTest.java` (PASS) | **VERIFIED** |

---

## 2. Requirements Traceability Quantitative Summary
- **Total Requirements Tracked:** 39
- **Verified (`VERIFIED`):** 39 (100.0%)
- **Missing (`MISSING`):** 0
- **Partially Implemented (`PARTIALLY IMPLEMENTED`):** 0
- **Test Failed (`TEST FAILED`):** 0
- **Blocked (`BLOCKED`):** 0
