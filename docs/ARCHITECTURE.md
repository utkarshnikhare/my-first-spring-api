# SocioMart — System & Application Architecture

This document details the end-to-end architecture of SocioMart, covering the frontend, backend, database, security, and deployment pipelines.

---

## 1. System Architecture Overview

SocioMart is a society-level marketplace platform designed to connect residents of residential communities with home-kitchen chefs and homemade creators.

The platform architecture follows a decoupled, cloud-native design:
- **Frontend Layer**: Served via Netlify edge CDN as a lightweight Progressive Web App (PWA) with zero client-side framework overhead.
- **API Proxy Layer**: Netlify transparently proxies all `/api/*` traffic to the backend, eliminating cross-origin resource sharing (CORS) complexity and cookie domain mismatch.
- **Backend Layer**: Java 21 / Spring Boot service deployed on Render (`sociomart-demo`), running on embedded Tomcat.
- **Database Layer**: Managed PostgreSQL 16 hosted on Supabase (AWS Tokyo, `ap-northeast-1`), utilizing connection pooling.
- **Session Layer**: Database-backed HTTP sessions managed via Spring Session JDBC (`SPRING_SESSION` table), allowing sessions to survive container restarts.

```
[ Buyer / Seller / Admin Browser ]
               │
               ▼
       [ Netlify Edge CDN ]
         ├── Static Assets (HTML, CSS, JS, PWA icons)
         └── Proxy Rewrite (/api/*)
               │
               ▼ (HTTPS)
      [ Render Web Service ]
         └── Spring Boot Application (Port 8081)
               ├── Spring Security Filter Chain (RBAC, CSRF)
               ├── REST Controllers & Exception Handlers
               ├── Business Services & Domain Logic
               └── Spring Data JPA / Hibernate (ddl-auto=validate)
                     │
                     ▼ (HikariCP / TLS)
          [ Supabase PostgreSQL 16 ]
               ├── Schema (Flyway Migrations V1-V27)
               ├── Business Data (Orders, Offerings, Stores)
               └── Session Persistence (SPRING_SESSION)
```

---

## 2. Frontend Architecture

### Technology Stack
- **Languages**: Semantic HTML5, Vanilla JavaScript (ES6+), CSS3 with Custom Properties.
- **Bundle Strategy**: Native browser modules without bundlers (Webpack/Vite/Rollup), ensuring instant builds, rapid debugging, and zero transpilation drift.
- **Design System**:
  - *Warm Editorial Light Theme*: Background `#F5EFE6`, Surface `#FFFFFF`, Surface-2 `#E6D5C2`, Text `#44352F`, Brand `#A96748`.
  - *Espresso Dark Theme*: Background `#1C1613`, Surface `#261F1B`, Surface-2 `#332B26`, Text `#F5EFE6`, Muted `#A89B93`.
  - Auto-adapts to `prefers-color-scheme` and supports manual toggling via `#themeToggle` with `localStorage` persistence.

### Portals & Routing
1. **Buyer Portal (`index.html`)**:
   - Single Page Application (SPA) driven by hash routing (`#/home`, `#/food`, `#/category/:name`, `#/favourites`, `#/orders`, `#/profile`).
   - Clean URLs (`/food`, `/kitchens`, `/kitchen/:slug`) are rewritten to `index.html` via Netlify rules.
   - **Progressive Loading**: Immediate shell and skeleton mounting via `foodHubShellHtml()` and background asynchronous hydration via concurrent promises (`catsPromise`, `itemsPromise`, `favPromise`).
2. **Seller Studio (`seller.html`)**:
   - SPA for seller onboarding, kitchen profile management, dynamic offering creation, stock allocation, and order fulfillment.
   - Immediate dashboard skeleton rendering with decoupled background order counts (`sellerOfferingOrderCounts()`).
3. **Admin Console (`admin.html`)**:
   - Operational management console for community approval, seller onboarding review, order lifecycle auditing, analytics tracking, and filter-aware CSV exports.

---

## 3. Backend Architecture

### Application Architecture
- **Framework**: Spring Boot 4.1.1 on Java 21 (with preview features enabled for virtual threads/performance).
- **Timezone Normalization**: All business dates, midnight expiry cron tasks, and cutoffs are bound strictly to `Asia/Kolkata` (IST) via `AppTimeZone`.
- **Database Migrations**: Flyway owns the schema lifecycle in production. Migrations V1 through V27 execute sequentially at startup before Hibernate validation.
- **Transaction Management**: Spring Declarative `@Transactional` ensures atomic operations across multi-entity writes (e.g., order creation, stock deduction, payment recording).

### Security & RBAC
- **Authentication**: Stateful session cookies with `CookieCsrfTokenRepository` (`X-XSRF-TOKEN`).
- **Authorization Hierarchy**:
  - `ROLE_BUYER`: Scoped to personal profile, orders, favourites, and public storefronts.
  - `ROLE_SELLER`: Scoped strictly to the seller's owned kitchen, products, schedules, and orders.
  - `ROLE_ADMIN`: Scoped to area/society moderation, user reviews, order audit, and CSV exports.
  - `ROLE_SUPER_ADMIN`: Full administrative control across all areas and system configurations.
- **Formula Injection Mitigation**: Exported CSVs automatically sanitize Excel/Calc formula prefixes (`=`, `+`, `-`, `@`, `\t`, `\r`) with leading single quotes.

---

## 4. PWA & Offline Strategy

- **Service Worker (`sw.js`)**: Implements dual caching strategies:
  - *Cache-First*: Static assets (HTML, CSS, JS, web fonts, app icons).
  - *Network-First / Network-Only*: All `/api/*` requests, ensuring private buyer/seller transactional data is never stored in persistent browser caches.
- **App Manifest (`manifest.webmanifest`)**: Provides full PWA installation metadata, standalone display mode, orientation locking, and theme colors.
