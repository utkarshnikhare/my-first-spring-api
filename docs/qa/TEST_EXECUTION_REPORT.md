# SocioMart Test Execution Report (refreshed 2026-10-27)

**Report Date:** 2026-10-27
**Auditor:** Principal Engineer / QA Lead (fresh audit)

## 1. Build & compilation

- Command: mvnw -B clean test (from my-first-spring-api/)
- Result: BUILD SUCCESS
- Java 21.0.11, Maven 3.9.9, Spring Boot 4.1.1

## 2. Unit / integration tests (JUnit)

- Tests run: 774
- Failures: 0
- Errors: 0
- Skipped: 5 (PostgreSQL-only — blocked, see section 4)
- Duration: ~2m39s

## 3. Playwright E2E

- Command: npx playwright test (from my-first-spring-api/e2e/)
- Result: 13 passed, 0 failed
- Projects: desktop (Chromium 1440x1000), mobile (Pixel 7)
- Duration: ~2.5m

## 4. Skipped / blocked tests

| Test | Count | Reason | Status |
|---|---|---|---|
| PostgresNativeMigrationTest | 1 | No PostgreSQL/Docker available | BLOCKED |
| PostgresRuntimeSmokeTest | 1 | No PostgreSQL/Docker available | BLOCKED |
| UserEmailMigrationRegressionTest | 3 | No PostgreSQL/Docker available | BLOCKED |

PostgreSQL integration and migration behavior is NOT verified in this environment. H2-only success is not proof of production DB compatibility.

## 5. Security tests

- AuthRateLimitSecurityTest: 7/7 pass.
- PublicHomemadeRoutingSecurityTest: pass (part of suite).

## 6. Not run

- Linting/static analysis: no dedicated linter configured in pom.
- Accessibility beyond Playwright checks: not separately run.
- Physical device testing: browser emulation only.
- Production smoke tests: read-only health check only ({"status":"UP"}).

## 7. Summary

- Passed: 774 JUnit + 13 Playwright = 787 tests.
- Failed: 0.
- Skipped/blocked: 5.
- Not run: Postgres integration, lint, physical devices, full production smoke.
