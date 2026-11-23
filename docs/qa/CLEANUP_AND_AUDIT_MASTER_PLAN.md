# SocioMart — Complete Project Inventory, Architecture Baseline & Cleanup Plan

**Date:** November 23, 2026  
**Feature Branch:** `feature/complete-project-cleanup`  
**Base Commit:** `bfea8e7e340e24e15736cd2ffa47008aa3e2a2a9` (PR #139 merge)  
**Execution Status:** Stage A & B Complete (Discovery & Baseline Verified, Cleanup Plan Formulated)  

---

## 1. Executive Summary & Baseline Metrics

| Metric | Pre-Cleanup Baseline | Target / Planned State |
| :--- | :--- | :--- |
| **Git Tracked Files** | 487 files | ~455 files (redundant docs/quarantine removed) |
| **Active Maven Tests** | 940 run (923 passed, 17 skipped) | 940 run (923 passed, 17 skipped) — 100% pass |
| **Playwright E2E Tests** | 8 passed (0 failures) | 8 passed (0 failures) — 100% pass |
| **API Endpoints** | 182 endpoints | 182 endpoints (100% preserved for compatibility) |
| **Flyway Migrations** | 26 scripts (V1–V19, V21–V27) | 26 scripts (100% preserved, zero tampering) |
| **Direct Maven Dependencies**| 15 dependencies | 15 dependencies (100% active and required) |
| **Production DB State** | Supabase PostgreSQL 16 (Tokyo) | **100% UNCHANGED / ZERO MODIFICATION** |
| **Hosting Status** | Netlify + Render ($0 Free Plans) | **100% UNCHANGED / AUTO-DEPLOY OFF** |

---

## 2. Project Architecture Baseline

### 1. Repository Structure
- Root Directory: Contains orchestrator scripts, Dockerfile, Netlify and Render configurations, root documentation, and the primary application module `my-first-spring-api/`.
- `my-first-spring-api/`: Maven standard multi-layer Spring Boot application with embedded frontend assets in `src/main/resources/static/` and Playwright suites in `e2e/`.

### 2. Frontend Architecture
- Technology: Vanilla ES6+ JavaScript, semantic HTML5, and CSS custom properties (Variables). No heavy SPA framework (React/Angular/Vue) or bundling overhead.
- Portals:
  - Buyer Portal: `index.html` + `js/buyer.js` + `js/app.js` + `css/styles.css`
  - Seller Studio: `seller.html` + `js/seller.js` + `css/seller.css`
  - Admin Console: `admin.html` + `js/admin.js` + `css/admin.css`
- Design System: Warm Editorial Light Theme (`#F5EFE6`) & Espresso Dark Theme (`#1C1613`), high-contrast accessible tokens, responsive mobile-first grid and flexbox.

### 3. Backend Architecture
- Technology: Java 21 / Spring Boot 4.1.1 on embedded Tomcat (`PORT: 8081`).
- Layering: Standard Controller -> Service -> Repository -> JPA/Hibernate Entity hierarchy.
- Concurrency & Transactions: HikariCP connection pooling, Spring Declarative `@Transactional`, explicit pessimistic/optimistic concurrency controls on stock.

### 4. Database Architecture
- Production: Supabase Managed PostgreSQL 16 with connection pooling (`aws-0-ap-northeast-1.pooler.supabase.com:5432`).
- Local/Testing: In-memory H2 database with PostgreSQL compatibility mode, plus Dockerized PostgreSQL 16 Testcontainers for CI.
- Schema Management: Flyway versioned migrations (V1 through V27). Hibernate configured with `ddl-auto=validate` in production.

### 5. Authentication & Authorisation Architecture
- Session-Based Identity: Spring Session JDBC storing session states in `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES` tables.
- Role-Based Access Control: `BUYER`, `SELLER`, `ADMIN`, `SUPER_ADMIN`.
- Session Isolation: Distinct session cookies (`JSESSIONID`) with HTTP-only and SameSite flags; explicit teardown and session invalidation on logout.

### 6. API Communication & Routing
- Restful JSON endpoints prefixed with `/api/`.
- Client Proxies: In production, Netlify proxies `/api/*` directly to `https://sociomart-demo.onrender.com/api/:splat` with `200! force = true`.
- Clean Public URLs: Netlify redirects clean buyer routes (`/food`, `/kitchens`, `/kitchen/:slug`, `/orders`) to `/index.html`.

### 7. PWA & Service Worker Architecture
- Manifest: `manifest.webmanifest` and fallback `manifest.json`.
- Service Worker: `sw.js` with Cache-First asset precaching (`CACHE_VERSION = 'sociomart-v4'`) and Network-First API strategy. Explicitly excludes private user endpoints from cache.

### 8. Build, Deployment & CI/CD Architecture
- CI Engine: GitHub Actions at `.github/workflows/ci.yml`.
- Testing: Maven Surefire runner with Asia/Kolkata timezone normalization.
- Deployment: Netlify CLI for frontend static assets; manual Render webhook/API deployment targeting exact commit SHAs. Auto-deploy disabled.

---

## 3. Evidence-Based Cleanup Inventory & Action Plan

### A. Confirmed Redundant Files for Safe Removal

| Item / Path | Category | Evidence & Verification | Removal Reason | Risk Assessment |
| :--- | :--- | :--- | :--- | :--- |
| `my-first-spring-api/README.md` | Doc / Scratch | Verified content: 34 lines of obsolete investigation notes from PR #73 regarding `MidnightExpiryJob`. | Not a real README; misleads developers. | **Zero Risk**: Not referenced by build or runtime. |
| `my-first-spring-api/.github/workflows/ci.yml` | CI Config | GitHub Actions only reads from root `.github/workflows/`. | Redundant, out-of-sync duplicate file ignored by GitHub. | **Zero Risk**: Verified ignored by GitHub. |
| `my-first-spring-api/netlify.toml` | Host Config | Root `netlify.toml` configures publish dir `my-first-spring-api/src/main/resources/static`. | Redundant duplicate ignored by Netlify root builds. | **Zero Risk**: Netlify operates from root. |
| `docs/quarantine/.design-seller-dashboard-reference.png` | Binary Asset | 1.67 MB screenshot quarantined in October. Checked across all code and docs. | Dead graphic asset bloating repository. | **Zero Risk**: Unused by runtime or active documentation. |
| `my-first-spring-api/requirements_matrix.md` | Obsolete Matrix | Verification matrix dated Oct 9, 2026 referencing old commit `702679c`. | Superseded by modern defect and trace matrices in `docs/`. | **Zero Risk**: Purely historical scratch file. |

### B. Documentation Consolidation Plan
Consolidate fragmented, overlapping defect registers and test reports into authoritative reference documents in `docs/`:
- Consolidate legacy scratch notes (`docs/FINAL-DEFECT-REGISTER.md`, `docs/DEEP_QA_DEFECT_REGISTER.md`) into authoritative registers.
- Deploy the authoritative 10-part documentation suite specified in Phase 14:
  1. `README.md` (Authoritative root entry point)
  2. `docs/ARCHITECTURE.md`
  3. `docs/PROJECT_STRUCTURE.md`
  4. `docs/API_REFERENCE.md`
  5. `docs/DATABASE.md`
  6. `docs/DEVELOPMENT.md`
  7. `docs/DEPLOYMENT.md`
  8. `docs/SECURITY.md`
  9. `docs/TESTING.md`
  10. `CHANGELOG.md`

### C. Retained Components (Zero Deletion Rationale)
- **All 182 API Endpoints**: Retained 100%. No endpoints will be deleted. Preserves complete API backwards compatibility across all third-party and client surfaces.
- **All 26 Flyway Migrations**: Retained 100%. Never renumbered or altered.
- **All 15 Maven Dependencies**: Retained 100%. All are actively imported and required by production or testing.
- `empty.js` in static resources: Retained. Actively precached by `sw.js` and used for Netlify HUD neutralization.

---

## 4. Security Hardening Plan (Phase 8)

1. **Security Headers Hardening**:
   - Verify `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, and `X-Frame-Options: SAMEORIGIN` across both `SecurityConfig.java` and `netlify.toml`.
2. **Formula Injection Defense**:
   - Confirmed active in `AdminService.java` (`neutraliseFormula`) and covered by `AdminExportTest.java`.
3. **Session Cookie Security**:
   - Ensure session cookies enforce `HttpOnly` and `SameSite=Lax/Strict`.
4. **Secret Scanning**:
   - Automated scan confirmed zero exposed private keys, GitHub tokens, Render API keys, or live database passwords in git-tracked files.
