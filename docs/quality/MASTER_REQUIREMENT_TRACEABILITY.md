# SocioMart Master Requirement Traceability Matrix (RTM)

**Document Version:** 4.0.0  
**Effective Date:** 2026-11-09  
**Application:** SocioMart Community Marketplace  
**Repository:** `https://github.com/utkarshnikhare/my-first-spring-api`  
**Current Main Commit:** `32a5c2978b20fe1730a0cd49c5e30f317498bc45`  
**Production Endpoint:** `https://sociomart-demo.onrender.com`  
**Render Service ID:** `srv-dad5lfajnfac73ei06s0`  

---

## 1. Traceability Overview & Methodology

This document maps all authoritative SocioMart business requirements, technical specifications, and quality mandates to their corresponding backend, frontend, database implementations, and verification evidence.

Status Legend:
- **PASS:** Fully implemented and verified with passing automated test evidence (JUnit / MockMvc / Playwright / CI).
- **BLOCKED:** Implementation complete or designed, but external dependency prevents end-to-end execution.
- **FAIL:** Feature fails verification assertions.

---

## 2. Requirement Traceability Table

### A. Buyer Application & Discovery Workflows

| Req ID | Original Requirement | Components (BE / FE / DB) | Implementation Status | Verification Method | Test Evidence | Remaining Work | Status |
|---|---|---|---|---|---|---|---|
| **REQ-BYR-01** | Kitchen & Homemade store discovery by society and area | `DiscoveryService.java`, `buyer.js`, `kitchens` table | Implemented | MockMvc + Playwright E2E | `HomemadeProductsTest.java`, `buyer-registration.spec.cjs` | None | **PASS** |
| **REQ-BYR-02** | Category filters & meal shortcuts (Breakfast, Lunch, Dinner, Snacks, Homemade) | `MarketplaceController.java`, `buyer.js` (`capsule-row`) | Implemented | Service Test + Playwright | `BuyerSellerUiUpdateTest.java`, `visual-qa.spec.cjs` | None | **PASS** |
| **REQ-BYR-03** | Product cards display price, unit, descriptions, and scarcity badge ("Only N left") | `buyer.js`, `styles.css` (`.oc-scarcity`) | Implemented | Visual QA + MockMvc | `BuyerDeliveryBadgeStructureTest.java`, `visual-qa.spec.cjs` | None | **PASS** |
| **REQ-BYR-04** | Expired item filtering: past-date offerings hidden from buyer discovery | `AvailabilityService.java`, `OfferingTiming.java` | Implemented | Boundary Unit Tests | `PreorderMidnightDefectReproductionTest.java` (PASS) | None | **PASS** |
| **REQ-BYR-05** | Cart persistence across page refresh and navigation | `buyer.js` (`localStorage`), `index.html` | Implemented | E2E Browser Session Test | `BuyerCheckoutSessionRecoveryTest.java`, `marketplace-lifecycle.spec.cjs` | None | **PASS** |
| **REQ-BYR-06** | Quantity sheet with +/- counter, notes, and fulfillment date display | `buyer.js` (`openQtySheet`), `styles.css` (`.sheet`) | Implemented | Playwright E2E | `visual-qa.spec.cjs` (390x844 sheet snapshot) | None | **PASS** |
| **REQ-BYR-07** | Multi-item checkout across multiple items with atomic validation | `OrderService.java`, `buyer.js` | Implemented | Concurrency Integration Test | `MultiUserConcurrencyAndAdversarialSecurityTest.java` | None | **PASS** |
| **REQ-BYR-08** | Pay Later workflow: order confirmed as UNPAID with seller verification needed | `OrderService.java`, `PaymentStatus.java`, `buyer.js` | Implemented | Spring Integration Test | `BuyerPayLaterRegressionTest.java`, `OrderServicePaymentTest.java` | None | **PASS** |
| **REQ-BYR-09** | Direct UPI QR code and payment ID presentation | `buyer.js`, `styles.css` (`.qr-box`, `.upi-row`) | Implemented | Playwright E2E | `visual-qa.spec.cjs` | None | **PASS** |
| **REQ-BYR-10** | Order cancellation by buyer prior to fulfillment with stock restoration | `OrderService.java` (`cancelOrderLocked`), `buyer.js` | Implemented | Integration Test | `InventoryRestorationIntegrationTest.java`, `visual-qa.spec.cjs` | None | **PASS** |
| **REQ-BYR-11** | Buyer order history with live status tracking (`CONFIRMED`, `PREPARING`, `READY`, `DELIVERED`, `CANCELLED`) | `BuyerOrderController.java`, `buyer.js` | Implemented | E2E Integration | `marketplace-lifecycle.spec.cjs` | None | **PASS** |
| **REQ-BYR-12** | Favourites management (toggle heart icon, view favourites list) | `FavouriteService.java`, `buyer.js` | Implemented | Unit + E2E | `FavouriteServiceLimitTest.java`, `visual-qa.spec.cjs` | None | **PASS** |

---

### B. Seller Application & Operations

| Req ID | Original Requirement | Components (BE / FE / DB) | Implementation Status | Verification Method | Test Evidence | Remaining Work | Status |
|---|---|---|---|---|---|---|---|
| **REQ-SLR-01** | Seller registration with business type selection (`KITCHEN`, `HOMEMADE_PRODUCTS`, `BOTH`) | `SellerController.java`, `seller.js`, `SellerType.java` | Implemented | E2E Playwright | `mobile-registration.mobile.spec.cjs` | None | **PASS** |
| **REQ-SLR-02** | Seller dashboard with daily order counts, earnings, and quick action bar | `SellerAppService.java`, `seller.js`, `seller.css` | Implemented | UI Component Test | `SellerDashboardUiTest.java` (11/11 PASS) | None | **PASS** |
| **REQ-SLR-03** | Offering creation with preset categories (Lunch, Dinner, etc.), timing, and prices | `SellerService.java`, `seller.js` (`createOfferingForm`) | Implemented | Service Validation Test | `SellerServiceCreateOfferingValidationTest.java` | None | **PASS** |
| **REQ-SLR-04** | Quick Post creation: rapid offering with image and text broadcast | `SellerQuickPostService.java`, `V16`, `V18` migrations | Implemented | Integration Test | `SellerFavouriteQuickPostIntegrationTest.java` | None | **PASS** |
| **REQ-SLR-05** | Pause and Resume offering availability from dashboard | `SellerAppService.java` (`pauseOrders`, `resumeOrders`) | Implemented | Service Test | `SellerAppControllerDateParsingTest.java` | None | **PASS** |
| **REQ-SLR-06** | Manual Sold Out terminal policy: seller action marks occurrence terminal; cannot resume | `SellerAppService.java`, `V23__add_manual_sold_out_state.sql` | Implemented | Regression Test Suite | `PostgresOfferingLifecycleMigrationTest.java`, PR #115 | None | **PASS** |
| **REQ-SLR-07** | Automatic Sold Out upon capacity exhaustion; reopens on eligible cancellation | `ProductRepository.java` (`consumeStock`), `OrderService.java` | Implemented | Concurrency Test | `MultiUserConcurrencyAndAdversarialSecurityTest.java` | None | **PASS** |
| **REQ-SLR-08** | Order status lifecycle management: mark `PREPARING`, `READY`, `DELIVERED` | `SellerAppService.java`, `seller.js` | Implemented | E2E Lifecycle Test | `marketplace-lifecycle.spec.cjs` | None | **PASS** |
| **REQ-SLR-09** | Mark payment received (`PaymentStatus.PAID`) independent of delivery status | `OrderService.java` (`markOrderAsPaid`), `seller.js` | Implemented | Service State Machine Test | `OrderServicePaymentTest.java` (PASS) | None | **PASS** |
| **REQ-SLR-10** | Seller earnings calculation and order summary excluding cancelled orders | `SellerAppService.java`, `seller.js` | Implemented | Accounting Test | `SellerEarningsUiTest.java`, `RevenueReconciliationTest.java` | None | **PASS** |
| **REQ-SLR-11** | Customer order drilldown: viewing customer notes, address, phone, and items | `SellerAppService.java`, `seller.js` (`sellerOrderDetailView`) | Implemented | UI Integration Test | `SellerOrderDetailUiTest.java` | None | **PASS** |

---

### C. Homemade Products & Specialized Storefronts

| Req ID | Original Requirement | Components (BE / FE / DB) | Implementation Status | Verification Method | Test Evidence | Remaining Work | Status |
|---|---|---|---|---|---|---|---|
| **REQ-HMP-01** | Draft saving for homemade offerings without public buyer visibility | `Product.java` (`lifecycleState="DRAFT"`), `V25`, `V26` | Implemented | Service Test | `HomemadeProductsTest.java` | None | **PASS** |
| **REQ-HMP-02** | Product preview modal rendering exact buyer card before publish | `seller.js` (`openProductPreviewModal`), `seller.css` | Implemented | Component Test | `SellerKitchenPreviewTest.java` | None | **PASS** |
| **REQ-HMP-03** | Product availability modes: Always Available, Made on Request, Specific Date | `AvailabilityType.java`, `Product.java`, `seller.js` | Implemented | Comprehensive Suite | `HomemadeProductsTest.java` (5/5 PASS) | None | **PASS** |
| **REQ-HMP-04** | Inventory modes: Limited Quantity (atomic decrement) vs No Limit (unbounded) | `ProductRepository.java`, `OrderService.java` | Implemented | Concurrency Test | `MultiUserConcurrencyAndAdversarialSecurityTest.java` | None | **PASS** |
| **REQ-HMP-05** | Units of measurement (Piece, 250g, 500g, 1kg, Box, Pack) | `ProductCreateDto.java`, `seller.html` | Implemented | DTO Validation Test | `SellerServiceCreateOfferingValidationTest.java` | None | **PASS** |
| **REQ-HMP-06** | Clean public shareable storefront URL (`/homemade-store/{slug}`) | `BuyerPublicController.java`, `SecurityConfig.java` | Implemented | Security + E2E | `PublicHomemadeRoutingSecurityTest.java`, `homemade-storefront.spec.cjs` | None | **PASS** |
| **REQ-HMP-07** | Category separation: Kitchen meals on `/kitchen/{slug}`, Homemade on `/homemade-store/{slug}` | `ProductDto.getIsHomemade()`, `buyer.js` | Implemented | Integration Test | `PublicHomemadeRoutingSecurityTest.java` | None | **PASS** |

---

### D. Admin Console & Platform Governance

| Req ID | Original Requirement | Components (BE / FE / DB) | Implementation Status | Verification Method | Test Evidence | Remaining Work | Status |
|---|---|---|---|---|---|---|---|
| **REQ-ADM-01** | Admin authentication and role-based access control (`ROLE_ADMIN`, `ROLE_SUPER_ADMIN`) | `SecurityConfig.java`, `AdminController.java`, `admin.js` | Implemented | Security MockMvc | `AdminServiceAdminManagementTest.java` | None | **PASS** |
| **REQ-ADM-02** | Seller application review, approval, change requests, and rejection | `AdminService.java`, `admin.js` (`renderApprovals`) | Implemented | E2E Playwright | `visual-qa.spec.cjs` (approvals workflow) | None | **PASS** |
| **REQ-ADM-03** | Society and area management (location directory isolation) | `AdminLocationService.java`, `admin.js` | Implemented | Service Test | `AdminLocationsManagementTest.java`, `SocietyDirectoryTest.java` | None | **PASS** |
| **REQ-ADM-04** | Seller suspension and reactivation with immediate storefront unpublishing | `AdminService.java`, `Kitchen.java` | Implemented | Security Test | `AdminHandoverGapTest.java` | None | **PASS** |
| **REQ-ADM-05** | Platform revenue metrics excluding cancelled orders | `AdminService.java` (`dashboard`), `admin.js` | Implemented | Service Test | `RevenueReconciliationTest.java`, `AdminDashboardOrderStatusTest.java` | None | **PASS** |
| **REQ-ADM-06** | Admin audit logging of all sensitive administrative actions | `AdminAuditService.java`, `admin_audit_logs` table | Implemented | Integration Test | `AdminAuditServicePaginationTest.java` | None | **PASS** |

---

### E. Scheduling, Timing & Cutoff Engine

| Req ID | Original Requirement | Components (BE / FE / DB) | Implementation Status | Verification Method | Test Evidence | Remaining Work | Status |
|---|---|---|---|---|---|---|---|
| **REQ-TIM-01** | Strict Indian Standard Time (`Asia/Kolkata`) across JVM, DB, and browsers | `AppTimeZone.java`, `OfferingTiming.java`, `buyer.js`, `seller.js` | Implemented | Timezone Unit Suite | `BusinessTimeZoneTest.java` (47 timing tests) | None | **PASS** |
| **REQ-TIM-02** | Cutoff time evaluation relative to fulfillment date (Pre-order defect fix) | `OfferingTiming.java` (`isPastCutoff`) | Implemented | Reproduction Suite | `PreorderMidnightDefectReproductionTest.java` (Tests A-H) | None | **PASS** |
| **REQ-TIM-03** | Orders Open From enforcement: cannot order before opening timestamp | `OfferingTiming.java`, `V15__add_orders_open_from.sql` | Implemented | E2E + Unit | `orders-open-from.spec.cjs`, `Requirement5LiveOfferingEditTest.java` | None | **PASS** |
| **REQ-TIM-04** | Midnight expiry job: cleans expired occurrences and generates new recurring occurrences | `MidnightExpiryJob.java`, `@Scheduled` | Implemented | Scheduled Job Test | `MidnightExpiryJobTest.java`, `SchedulerWiringAndRecoveryTest.java` | None | **PASS** |
| **REQ-TIM-05** | Dynamic order vs pre-order classification based on date and cutoff | `OfferingTiming.java`, `buyer.js` | Implemented | UI + E2E | `controlled-clock-workflows.spec.cjs` | None | **PASS** |

---

### F. Security, Concurrency & Data Integrity

| Req ID | Original Requirement | Components (BE / FE / DB) | Implementation Status | Verification Method | Test Evidence | Remaining Work | Status |
|---|---|---|---|---|---|---|---|
| **REQ-SEC-01** | Rate limiting with right-most proxy IP extraction (WEAK-03 fix) | `AuthController.java` (`clientIp`), `AuthRateLimiter.java` | Implemented | Adversarial MockMvc | `AuthRateLimitSecurityTest.java` (8/8 PASS) | None | **PASS** |
| **REQ-SEC-02** | Multi-item checkout deadlock prevention via TreeMap lock ordering (WEAK-10 fix) | `OrderService.java` (`TreeMap` usage in stock mutices) | Implemented | Concurrent Multi-User Test | `MultiUserConcurrencyAndAdversarialSecurityTest.java` (11/11 PASS) | None | **PASS** |
| **REQ-SEC-03** | Recurring schedule overselling prevention via pessimistic locking (WEAK-01 fix) | `RecurringScheduleRepository.java`, `OrderService.java` | Implemented | Thread Contention Test | `RecurringBuyerOrderFlowTest.java` (9/9 PASS) | None | **PASS** |
| **REQ-SEC-04** | Paid order cancellation refund follow-up flagging (WEAK-02 fix) | `OrderService.java` (`cancelOrderLocked`) | Implemented | Service Payment Test | `OrderServicePaymentTest.java` (9/9 PASS) | None | **PASS** |
| **REQ-SEC-05** | Role boundary protection: buyers cannot access seller/admin APIs | `UserSessionAuthorizationFilter.java`, `SecurityConfig.java` | Implemented | Security Tests + E2E | `visual-qa.spec.cjs`, `EnquiryMutationSecurityTest.java` | None | **PASS** |
| **REQ-SEC-06** | Stored XSS defense via HTML entity escaping on all user content | `common.js` (`esc()`), `xss-defense.spec.cjs` | Implemented | Playwright XSS Test | `xss-defense.spec.cjs` (PASS) | None | **PASS** |
| **REQ-SEC-07** | CSRF token protection on all mutable HTTP endpoints | `SecurityConfig.java` (`CookieCsrfTokenRepository`) | Implemented | HTTP Tests | `DirectRegistrationSecurityIntegrationTest.java` | None | **PASS** |

---

### G. Responsive UI & Accessibility

| Req ID | Original Requirement | Components (BE / FE / DB) | Implementation Status | Verification Method | Test Evidence | Remaining Work | Status |
|---|---|---|---|---|---|---|---|
| **REQ-UI-01** | Small mobile (320px x 568px): zero horizontal overflow, wrapped tiles, readable text | `styles.css`, `seller.css`, `admin.css` | Implemented | Playwright 9 Viewports | `visual-qa.spec.cjs` (320x568 PASS) | None | **PASS** |
| **REQ-UI-02** | Standard mobile (360px & 375px): 44px touch targets, mobile bottom nav | Responsive CSS stylesheets | Implemented | Playwright 9 Viewports | `visual-qa.spec.cjs` (360x800 PASS) | None | **PASS** |
| **REQ-UI-03** | Large mobile (390px & 430px): stacked action buttons, full-width sheets | Responsive CSS stylesheets | Implemented | Playwright 9 Viewports | `visual-qa.spec.cjs` (390x844 & 430x932 PASS) | None | **PASS** |
| **REQ-UI-04** | Tablet portrait (768px x 1024px): 2-column grids, centered navigation | Responsive CSS stylesheets | Implemented | Playwright 9 Viewports | `visual-qa.spec.cjs` (768x1024 PASS) | None | **PASS** |
| **REQ-UI-05** | Tablet landscape (1024px x 768px): 3-column grids, expanded categories | Responsive CSS stylesheets | Implemented | Playwright 9 Viewports | `visual-qa.spec.cjs` (1024x768 PASS) | None | **PASS** |
| **REQ-UI-06** | Laptop & Desktop (1280px, 1440px, 1920px): max container bounds, balanced whitespace | Responsive CSS stylesheets | Implemented | Playwright 9 Viewports | `visual-qa.spec.cjs` (1280, 1440, 1920 PASS) | None | **PASS** |
| **REQ-UI-07** | Dark theme support (`data-theme="dark"`): WCAG AA contrast compliance | CSS design tokens (`--surface`, `--text`, etc.) | Implemented | Playwright Theme Audits | `visual-qa.spec.cjs` (Theme toggle PASS) | None | **PASS** |

---

### H. Production Operations & Database Protection

| Req ID | Original Requirement | Components (BE / FE / DB) | Implementation Status | Verification Method | Test Evidence | Remaining Work | Status |
|---|---|---|---|---|---|---|---|
| **REQ-OPS-01** | Render deployment safety: `autoDeploy: false` strictly enforced in `render.yaml` | `render.yaml` (line 27) | Implemented | CI Static Check | `ci.yml` line 48 validation step | None | **PASS** |
| **REQ-OPS-02** | Production database backup script with AES-256-GCM encryption & SHA-256 checksums | `scripts/backup-database.sh`, `BackupCrypto.java` | Implemented | Script Verification | `DatabaseBackupAndRestoreVerificationTest.java` | None | **PASS** |
| **REQ-OPS-03** | Restore target isolation: fail-closed checks preventing restore to production | `scripts/restore-database.sh`, `test-restore-safeguards.sh` | Implemented | Automated Safeguard Suite | `test-restore-safeguards.sh` (16/16 PASS) | None | **PASS** |
| **REQ-OPS-04** | Live production database backup execution via GitHub Actions workflow | `.github/workflows/database-backup.yml` | Implemented | Manual Workflow Trigger | Run `42642434016` (Blocked on secrets) | Configure GitHub repository secrets | **BLOCKED** |
| **REQ-OPS-05** | Production application health & version endpoints (`/api/health`, `/api/version`) | `HealthController.java`, `VersionController.java` | Implemented | Live HTTP Inspection | Verified: `/api/health` 200 UP, `/api/version` matches commit `32a5c29` | None | **PASS** |

---

## 3. Traceability Sign-Off

Total requirements audited: **44**.  
- **Passed:** 43  
- **Blocked:** 1 (Live production database backup requires owner repository secret setup)  
- **Failed:** 0  
