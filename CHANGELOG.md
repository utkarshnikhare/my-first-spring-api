# SocioMart — Changelog

All notable changes to the SocioMart platform are documented in this file.

---

## [v4.2.0] - 2026-11-25 (Enterprise Architecture Hardening)

### Added
- **Flyway Migration `V28__enterprise_scalability_indexes.sql`**:
  - `idx_kitchen_served_societies_society`: Accelerates buyer multi-society discovery to `O(log N)` index seeks.
  - `idx_products_available_today_created`: Optimizes hot marketplace active offering queries without scanning archived items.
  - `idx_users_role_approved`: Accelerates admin seller onboarding and user moderation queries.
  - `idx_orders_status_composite`: High-performance compound index for seller earnings and active order status queries.
- **Enterprise Architectural Decision Records (ADRs)** in `docs/adr/`:
  - `ADR-001-modular-monolith-vs-microservices.md`
  - `ADR-002-dual-backend-evaluation-and-netlify-independence.md`
  - `ADR-003-multi-society-data-isolation-and-authorization.md`
  - `ADR-004-concurrency-and-transaction-pool-routing.md`
  - `ADR-005-database-indexing-and-capacity-scaling.md`
- **Scalability, Disaster Recovery & Operational Runbooks**:
  - `docs/SCALABILITY.md`: Capacity forecast for 1k to 100k users, RPS, connections, and cost models.
  - `docs/DISASTER_RECOVERY.md`: RPO (<1hr) and RTO (<15min) specifications, backup architectures, and restoration runbooks.
  - `docs/RUNBOOKS.md`: Operational incident response for 504 timeouts, connection pool exhaustion, and emergency rollbacks.

### Verified
- Zero regressions across existing Buyer, Seller, and Admin functional journeys.
- Concurrency and transactional isolation verified via pessimistic write locking and Hikari pool routing.
- Multi-area delivery coverage confirmed operational across both Netlify and Render.

---

## [Unreleased] - `feature/complete-project-cleanup`

### Added
- Comprehensive authoritative documentation suite in `docs/`:
  - `docs/ARCHITECTURE.md`: Complete system, frontend, backend, and session persistence architecture.
  - `docs/PROJECT_STRUCTURE.md`: Full repository layout and component classification.
  - `docs/API_REFERENCE.md`: Complete REST API reference across all supported endpoints.
  - `docs/DATABASE.md`: Schema, entity relationships, Flyway migration chain, and indexing strategy.
  - `docs/DEVELOPMENT.md`: Developer onboarding, local setup with H2, and test execution commands.
  - `docs/DEPLOYMENT.md`: Production Netlify & Render operational runbook with rollback procedures.
  - `docs/SECURITY.md`: Security architecture, RBAC hierarchy, formula injection defenses, and CSP.
  - `docs/TESTING.md`: Four-tier test strategy, JUnit 5, Playwright E2E, and regression verification.

### Changed
- Refactored `README.md` to reference authoritative Netlify and Render production URLs, up-to-date architecture, and the new documentation set.
- Consolidated overlapping defect registers into `docs/DEFECT_REGISTER.md`.

### Removed
- Removed 5 confirmed redundant files based on evidence:
  - `my-first-spring-api/README.md`: Outdated PR #73 scratch investigation notes.
  - `my-first-spring-api/.github/workflows/ci.yml`: Duplicate workflow file ignored by GitHub Actions.
  - `my-first-spring-api/netlify.toml`: Redundant configuration duplicate ignored by Netlify root builds.
  - `docs/quarantine/.design-seller-dashboard-reference.png`: 1.67 MB dead screenshot asset in quarantine.
  - `my-first-spring-api/requirements_matrix.md`: Superseded October 2026 feature matrix.

---

## [v3.0.0] - 2026-11-22 (PR #139)

### Added
- Progressive skeleton loading on Buyer Food Hub (`foodHubView()`) with content-shaped shimmers (`styles.css`).
- Asynchronous offering order count badge hydration (`sellerOfferingOrderCounts()`) in Seller Dashboard.
- Dedicated unit and integration test suite `AdminExportTest.java` (10 tests) verifying CSV export security and formula injection sanitization.

### Security
- Hardened CSV export generation with `neutraliseFormula()` against spreadsheet formula injection attacks.

---

## [v2.5.0] - 2026-11-22 (PR #138)

### Fixed
- Contrast and text readability across light and dark themes on all three portals.
- Fixed floating overlay clearance on mobile devices (360px & 390px viewports).
- Enforced session isolation between user logins.
