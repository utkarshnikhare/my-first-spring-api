# SocioMart Feature Traceability Matrix

**Generated Date:** 2026-10-31  
**Scope:** Complete verification baseline across Buyer, Seller, Admin, Background Workers, and Database Persistence layers.  
**Specification References:** SocioMart Requirements V1, Recurring Offerings V2, Seller Dashboard V3, Defect Batch 1 (DEF-01 to DEF-09), V20 Payment Verification States, and V21 Payment Configuration Snapshotting.

---

## 1. Traceability Overview

| Category | Endpoints / Routes | Service / Component | Entities | Test Coverage Status |
| :--- | :--- | :--- | :--- | :--- |
| **Buyer Discovery** | `GET /api/marketplace`<br>`GET /api/discovery/kitchens`<br>`GET /api/kitchens/{id}`<br>`GET /api/discovery/search` | `MarketplaceService`<br>`DiscoveryService`<br>`KitchenService` | `Kitchen`, `Product`, `Society`, `Area`, `Favourite` | PASS |
| **Buyer Cart & Checkout** | `POST /api/orders/draft`<br>`POST /api/orders/place`<br>`GET /api/orders/{id}` | `OrderService`<br>`OfferingTiming`<br>`AvailabilityService` | `Order`, `OrderItem`, `Product`, `Occurrence` | PASS |
| **Buyer Payment** | `POST /api/orders/{id}/payment-status`<br>`GET /api/orders/{id}/payment-config` | `OrderService`<br>`SellerPaymentConfiguration` | `Order`, `PaymentStatus`, `SellerPaymentMode` | PASS |
| **Buyer Orders & Profile** | `GET /api/orders/buyer/active`<br>`GET /api/orders/buyer/history`<br>`POST /api/orders/{id}/cancel`<br>`GET /api/buyer/profile` | `OrderService`<br>`BuyerService` | `Order`, `User` | PASS |
| **Buyer Engagement** | `POST /api/favourites/{kitchenId}`<br>`GET /api/favourites`<br>`POST /api/enquiries` | `FavouriteService`<br>`EnquiryService` | `Favourite`, `Enquiry`, `NotificationEvent` | PASS |
| **Seller Onboarding & Auth** | `POST /api/auth/seller/register`<br>`POST /api/auth/login`<br>`POST /api/auth/logout`<br>`GET /api/auth/me` | `AuthService`<br>`SellerService` | `User`, `Kitchen`, `SellerApprovalStatus` | PASS |
| **Seller Dashboard & Store** | `GET /api/seller/app/dashboard`<br>`GET /api/seller/app/kitchen`<br>`PUT /api/seller/app/kitchen`<br>`PUT /api/seller/app/payment-config` | `SellerAppService`<br>`SellerService`<br>`LocationService` | `Kitchen`, `Society`, `Area` | PASS |
| **Seller Offerings (Live & One-Time)** | `POST /api/seller/app/offerings`<br>`PUT /api/seller/app/offerings/{id}`<br>`PATCH /api/seller/app/offerings/{id}/toggle-sold-out` | `SellerService`<br>`OfferingTiming` | `Product`, `Kitchen` | PASS |
| **Seller Offerings (Recurring V2)** | `POST /api/recurring-schedules`<br>`GET /api/recurring-schedules/kitchen/{id}`<br>`PUT /api/recurring-schedules/{id}`<br>`PATCH /api/recurring-schedules/{id}/occurrences/{occId}`<br>`POST /api/recurring-schedules/{id}/end` | `RecurringScheduleService`<br>`OfferingTiming` | `RecurringSchedule`, `Occurrence`, `OccurrenceOverride` | PASS |
| **Seller Orders & Delivery** | `GET /api/seller/app/orders`<br>`GET /api/seller/app/orders/summary`<br>`PATCH /api/seller/app/orders/{id}/status`<br>`PATCH /api/seller/app/orders/{id}/payment-status`<br>`POST /api/seller/app/orders/mark-all-delivered` | `SellerAppService`<br>`OrderService` | `Order`, `OrderItem`, `PaymentStatus` | PASS |
| **Seller Quick Posts** | `POST /api/seller/app/quick-posts`<br>`GET /api/seller/app/quick-posts`<br>`DELETE /api/seller/app/quick-posts/{id}` | `SellerAppService`<br>`MidnightExpiryJob` | `QuickPost`, `Kitchen` | PASS |
| **Admin Operations** | `GET /api/admin/sellers/pending`<br>`POST /api/admin/sellers/{id}/approve`<br>`GET /api/admin/locations/areas`<br>`POST /api/admin/locations/areas`<br>`GET /api/admin/audit-logs` | `AdminService`<br>`AdminAuditService`<br>`LocationService` | `User`, `Area`, `Society`, `AdminAuditLog` | PASS |
| **Background Jobs** | Fixed cron execution (`MidnightExpiryJob`, `NotificationReminderJob`) | Scheduled tasks via Spring `@Scheduled` | `QuickPost`, `Product`, `NotificationEvent` | PASS |
| **Database Migrations** | Flyway V1 through V21 + V20 Java migration | Flyway migration runner | 21 versions of relational schema and constraints | PASS |

---

## 2. Granular Feature Matrix

### 2.1 Buyer Workflows

| Feature ID | Feature Description | API Route | Validation & Business Rules | Verified Status |
| :--- | :--- | :--- | :--- | :--- |
| **BUY-01** | Public Marketplace Landing | `GET /api/marketplace` | Publicly accessible; displays active kitchens, upcoming pre-orders, and quick posts without authentication. | PASS |
| **BUY-02** | Society Filtering & Discovery | `GET /api/discovery/kitchens?society={name}` | Kitchens must be filtered to those servicing the specified society; hidden/paused kitchens excluded. | PASS |
| **BUY-03** | Kitchen Detail & Menu Browsing | `GET /api/kitchens/{id}` or `GET /api/kitchens/slug/{slug}` | Returns kitchen info, payment modes, contact info, active offerings, and upcoming pre-order schedules. | PASS |
| **BUY-04** | Draft Order Creation & Stock Lock | `POST /api/orders/draft` | Enforces positive non-zero quantity, verifies remaining quantity, locks price from current DB snapshot. | PASS |
| **BUY-05** | Cutoff Time Enforcement | `POST /api/orders/draft`, `POST /api/orders/place` | Rejects order placement if current IST time exceeds offering cutoff time (handles midnight wrap correctly per DEF-09). | PASS |
| **BUY-06** | Orders Open From Gate | `POST /api/orders/draft`, `POST /api/orders/place` | Rejects order placement if current IST time is before `ordersOpenFrom` timestamp. | PASS |
| **BUY-07** | Order Placement & Number Generation | `POST /api/orders/place` | Generates unique human-readable order number; decrements stock atomically; assigns initial status `PENDING` / `AWAITING_PAYMENT`. | PASS |
| **BUY-08** | Payment Mode Selection | `POST /api/orders/place` | Validates chosen payment mode against kitchen's `acceptedPaymentMode` (GPay, UPI, Cash-only, or Both). | PASS |
| **BUY-09** | Payment Status Update (Buyer-side) | `POST /api/orders/{id}/payment-status` | Buyer can mark order `PAYMENT_SUBMITTED` with reference ID. Buyer cannot mark order `PAID` directly. | PASS |
| **BUY-10** | Order Cancellation | `POST /api/orders/{id}/cancel` | Buyer can cancel prior to seller acceptance or cutoff; restocks product inventory automatically. | PASS |
| **BUY-11** | Order History Drilldown | `GET /api/orders/{id}` | Enforces buyer tenant ownership (`order.buyer.id == currentBuyer.id`); returns itemized costs, delivery status, and payment snapshots. | PASS |
| **BUY-12** | Favourite Kitchens | `POST /api/favourites/{kitchenId}` | Limits favourites to max configured count; returns favourite kitchens on buyer home. | PASS |
| **BUY-13** | General Enquiry | `POST /api/enquiries` | Validates name, phone, message length; notifies admin/seller. | PASS |

---

### 2.2 Seller Workflows

| Feature ID | Feature Description | API Route | Validation & Business Rules | Verified Status |
| :--- | :--- | :--- | :--- | :--- |
| **SEL-01** | Seller Direct Registration | `POST /api/auth/seller/register` | Phone uniqueness check; password hashing; creates seller in `PENDING` state; binds kitchen profile. | PASS |
| **SEL-02** | Seller Login & Session | `POST /api/auth/login` | Session cookie `SOCIOMART_SESSION`; verifies role `SELLER`; checks `isApproved()` gate. | PASS |
| **SEL-03** | Seller Dashboard Analytics | `GET /api/seller/app/dashboard` | Returns views today, followers, active orders count, pending revenue, confirmed revenue. | PASS |
| **SEL-04** | Live Offering Management | `POST /api/seller/app/offerings` | Validates category permissions (Kitchen vs Homemade); enforces non-negative price and quantity; sets availability. | PASS |
| **SEL-05** | Sold Out / Order Window Toggle | `PATCH /api/seller/app/offerings/{id}/toggle-sold-out` | Toggles item sold out; immediately prevents new buyer order drafts. | PASS |
| **SEL-06** | Recurring Schedule Creation (V2) | `POST /api/recurring-schedules` | Day-of-week selection, default close/delivery time, default capacity, 90-day ongoing cap. | PASS |
| **SEL-07** | Recurring Occurrence Overrides | `PATCH /api/recurring-schedules/{id}/occurrences/{occId}` | Per-occurrence capacity and time overrides; protected against reductions below already booked orders. | PASS |
| **SEL-08** | Order Management & Filtering | `GET /api/seller/app/orders` | Filters by society, payment status, delivery status; strictly scoped to authenticated seller's kitchen. | PASS |
| **SEL-09** | Order Payment Verification | `PATCH /api/seller/app/orders/{id}/payment-status` | Seller transitions payment status: `AWAITING_SELLER_VERIFICATION` -> `SELLER_VERIFIED_PAID` or `REJECTED`. | PASS |
| **SEL-10** | Delivery Progression | `PATCH /api/seller/app/orders/{id}/status` | Valid status transitions (`PENDING` -> `ACCEPTED` -> `PREPARING` -> `OUT_FOR_DELIVERY` -> `DELIVERED`). | PASS |
| **SEL-11** | Bulk Delivery Completion | `POST /api/seller/app/orders/mark-all-delivered` | Marks all active accepted/out-for-delivery orders for date as `DELIVERED`; idempotent; excludes drafts/cancelled. | PASS |
| **SEL-12** | Quick Post Broadcast | `POST /api/seller/app/quick-posts` | Text character limit (500 chars); optional image; tagged with requestId; auto-expires at IST midnight. | PASS |
| **SEL-13** | Payment Configuration Settings | `PUT /api/seller/app/payment-config` | Configures UPI ID, GPay number, QR code URL, and accepted payment modes (UPI, GPAY, CASH_ONLY, BOTH). | PASS |

---

### 2.3 Admin & System Workflows

| Feature ID | Feature Description | API Route | Validation & Business Rules | Verified Status |
| :--- | :--- | :--- | :--- | :--- |
| **ADM-01** | Admin Authentication | `POST /api/auth/login` | Restricted to `ADMIN` or `SUPER_ADMIN` roles; enforces rate limiting. | PASS |
| **ADM-02** | Seller Approval Pipeline | `POST /api/admin/sellers/{id}/approve` | Updates `SellerApprovalStatus` to `APPROVED`; unblocks seller dashboard access; records audit log. | PASS |
| **ADM-03** | Location Master Management | `POST /api/admin/locations/areas`<br>`POST /api/admin/locations/societies` | Creates and links areas and societies; validates name uniqueness per area. | PASS |
| **ADM-04** | Audit Log Retention | `GET /api/admin/audit-logs` | Paginated chronological log of all sensitive administrative mutations. | PASS |
| **ADM-05** | Public Health & Version Probes | `GET /api/health`<br>`GET /api/version` | Unauthenticated public endpoints reporting database connectivity and Git release SHA. | PASS |
| **ADM-06** | Midnight Expiry Cron Job | Background `@Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Kolkata")` | Expires outdated Quick Posts and resets daily kitchen availability flags at IST midnight. | PASS |

---

## 3. Database Constraints & Migration Lineage

- **V1-V13**: Core baseline tables (`users`, `kitchens`, `products`, `orders`, `order_items`, `areas`, `societies`).
- **V14-V17**: `payment_method` column addition and defensive idempotent recovery on `orders`.
- **V18**: Quick Post idempotency and request deduplication (`request_id`).
- **V19**: Seller payment configuration (`accepted_payment_mode`, `upi_id`, `gpay_number`, `qr_code_image_url`).
- **V20**: Java migration dynamically replacing legacy payment check constraint using PostgreSQL OID relation ownership (`conrelid = tbl.oid`), allowing `AWAITING_SELLER_VERIFICATION` and `SELLER_VERIFIED_PAID`.
- **V21**: Snapshotting order payment configuration at placement time (`orders.seller_payment_mode_snapshot`, `upi_id_snapshot`, `gpay_number_snapshot`).
