# SocioMart Project Work History

**Document Version:** 1.0.0
**Audit Date:** 2026-10-27
**Auditor:** Principal Engineer / QA Lead (fresh audit)
**Source of truth:** Git history (outer repo main), surefire reports, Playwright report, live endpoint probe.

## 1. Repository state (baseline)

- Remote: https://github.com/utkarshnikhare/my-first-spring-api.git
- Branch: main (clean working tree, up to date with origin/main)
- Latest commit: c19c905 — fix(storefront): public homemade store routing, slug resolution, category isolation, and corrective QA audit (SM-024 to SM-027) (#86)
- Prior documented commit referenced in task: a0c9987 (docs(qa): document inventory, requirements traceability matrix, defect register, test execution report, and release report (#85)) — exists in history and is an ancestor of c19c905.
- Many feature/fix branches exist on origin; only main is the release branch.

## 2. Major features implemented (evidence in source + tests)

- Homemade Products module (seller business types Kitchen/Homemade/Both, category permissions, admin approval, draft/preview/publish/pause lifecycle, stock modes, availability types).
- Order & Payment Status Framework (independent order/payment states, buyer payment choice, seller verification, delivery/cancellation with reason).
- Expired items & midnight refresh (query-time availability pinned to Asia/Kolkata, idempotent midnight job, occurrence retention).
- Pre-order cutoff handling (cross-day ordering windows).
- Registration rate limiting with proxy IP extraction (SM-022).
- Public homemade storefront routing (SM-024/SM-025/SM-026).
- Concurrency/atomic stock deduction (ProductRepository.consumeStock).
- Responsive UI across 9 viewports (Playwright visual-qa).

## 3. Defects discovered and their status

See docs/qa/DEFECT_REGISTER.md (SM-001 ... SM-027). All 27 are marked resolved in code; verification evidence: 774 JUnit tests pass, 13 Playwright E2E tests pass. Postgres-backed tests (3 files, 5 tests) are SKIPPED because no PostgreSQL/Docker available locally — status BLOCKED, not verified.

## 4. Tests added and actual execution status (2026-10-27)

- JUnit: Tests run: 774, Failures: 0, Errors: 0, Skipped: 5 — BUILD SUCCESS (mvnw clean test, ~2m39s).
- Skipped: PostgresNativeMigrationTest (1), PostgresRuntimeSmokeTest (1), UserEmailMigrationRegressionTest (3) — require PostgreSQL.
- Playwright E2E: 13 passed (desktop + mobile projects) — npx playwright test, ~2.5m.
- AuthRateLimitSecurityTest: 7/7 pass.
- PublicHomemadeRoutingSecurityTest: included in suite, pass.

## 5. GitHub / CI

- Latest pushed commit on main: c19c905.
- CI workflow exists (.github/workflows/ci.yml); not re-run locally. GitHub Actions status not queried for this audit.
- autoDeploy: false in render.yaml — pushes do NOT auto-deploy.

## 6. Render

- Health endpoint https://sociomart-demo.onrender.com/api/health returns {"status":"UP"}.
- Deployed commit SHA is not exposed by the health endpoint; production SHA unverified.
- Per SM-027, previous reports claimed deployment of commits that were not actually live. This audit does not claim the latest commit is deployed.
- No Render API key available in this environment (RENDER_API_KEY unset), so a programmatic deploy/verify cannot be performed. Manual deploy via Render dashboard is required.

## 7. Work complete and independently verified

- Unit/integration test suite green (774/774 non-skipped).
- E2E suite green (13/13).
- Build compiles.

## 8. Work implemented but unverified

- PostgreSQL migration tests (blocked by missing Postgres).
- Production deployment of c19c905 (unverified).

## 9. Work incomplete / blocked

- Render deploy of latest commit (manual action required).
- Postgres integration verification (requires a PostgreSQL instance).

## 10. Claims in earlier reports that cannot be substantiated

- "100% requirement coverage / 0 unverified" in RTM — Postgres tests are skipped, so DB-specific behavior is unverified; status overstated.
- "Render deployed commit X" claims in earlier release reports — unverified due to autoDeploy: false.

## 11. Cleanup

See docs/qa/CLEANUP_MANIFEST.md. No destructive deletions performed in this audit beyond documentation consolidation.
