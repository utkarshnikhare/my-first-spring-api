# SocioMart — Testing Architecture & Verification Strategy

This document details the test framework, test commands, execution environments, and quality gates for the SocioMart project.

---

## 1. Testing Architecture & Tiers

The test suite is structured into four distinct verification tiers:

```
[ Tier 1: Local Unit & Integration Tests ]
  ├── JUnit 5 + Spring Boot Test
  ├── In-Memory H2 Database (PostgreSQL mode)
  └── 940 tests (923 executed locally, 17 container-specific skipped)

[ Tier 2: PostgreSQL Container Tests ]
  ├── Testcontainers PostgreSQL 16
  ├── Full schema migration & concurrency tests
  └── Executed in GitHub Actions CI environment

[ Tier 3: End-to-End & Playwright Tests ]
  ├── Playwright runner (@playwright/test)
  ├── Multi-viewport testing (360px, 390px, 1440px desktop)
  ├── Interactive buyer, seller, and admin user journeys
  └── Dark / light theme switching and persistence audits

[ Tier 4: Live Production Smoke Checks ]
  ├── Read-only health, version, and CORS verifications
  └── Performed against deployed Netlify and Render endpoints
```

---

## 2. Test Execution Commands

### A. Running the Backend Test Suite (Maven)
Run all unit, integration, and security tests against the in-memory H2 database:
```bash
cd my-first-spring-api
.\mvnw.cmd test
```
*Linux / macOS:*
```bash
cd my-first-spring-api
./mvnw test
```

### B. Running a Targeted Test Class
To run a specific test class (e.g., `AdminExportTest`):
```bash
cd my-first-spring-api
.\mvnw.cmd test -Dtest=AdminExportTest
```

### C. Running Playwright End-to-End Tests
Playwright tests are located in `my-first-spring-api/e2e/`.

```bash
cd my-first-spring-api/e2e
npm install
npx playwright test master_portal_interactive_audit.spec.cjs --project=desktop
npx playwright test deep_mobile_and_theme_audit.spec.cjs --project=desktop
npx playwright test seller-recurring-creation.spec.cjs --project=desktop
```

---

## 3. Key Regression Test Suites

| Test Class / Spec | Environment | Coverage & Focus |
| :--- | :--- | :--- |
| `AdminExportTest.java` | Spring MockMvc / H2 | 10 tests verifying 401/403/200, RFC-4180 headers, CSV audit metadata banners, empty datasets, and formula injection sanitization. |
| `PostgresDatabaseBackupAndRestoreTest.java` | Testcontainers | PostgreSQL native backup, gzip compression, AES-GCM encryption, and restore safeguards. |
| `RoutingTransactionManagerTest.java` | Spring JPA / H2 | Nested transaction depth, rollback boundaries, and thread-local state cleanup. |
| `master_portal_interactive_audit.spec.cjs` | Playwright E2E | Full interactive buyer discovery, cart, UPI copy, checkout, seller dashboard, and admin approval workflows. |
| `deep_mobile_and_theme_audit.spec.cjs` | Playwright E2E | Theme switching, dark theme contrast, `localStorage` persistence, and mobile 360px/390px navigation clearance. |
| `seller-recurring-creation.spec.cjs` | Playwright E2E | Verifies that new recurring schedule creation returns HTTP 410 GONE while existing schedule management remains 100% accessible. |

---

## 4. Quality Gates & Acceptance Criteria

Every code submission must satisfy:
1. **Zero New Test Failures**: `mvnw test` must return `BUILD SUCCESS`.
2. **Zero Unhandled Promise Rejections**: Console errors and failed promises during E2E navigation will fail the Playwright gate.
3. **Reproducible Execution**: Tests run with `-Duser.timezone=Asia/Kolkata` to prevent machine-dependent timezone drift.
