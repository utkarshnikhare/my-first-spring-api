# Runtime Validation Report

**Generated:** 2026-10-10
**Target:** SocioMart Spring Boot + static Buyer/Seller/Admin apps
**Branch:** `copilot/demo-registration-e2e` (not yet merged or deployed)

## Summary

| Step | Status | Exit code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Playwright started the demo-profile app; `/api/auth/config` returned readiness at `127.0.0.1:18082` |
| Maven tests | PASS | 0 | `.\mvnw.cmd -B test`: 592 tests, 0 failures, 0 errors, 1 skipped |
| Browser E2E | PASS | 0 | `npx playwright test`: 4 passed, 0 failed across desktop and mobile |

**Overall:** PARTIAL — the executed local checks passed, but live acceptance is incomplete. The current branch
has not yet run on GitHub `main` or Render. Deployment is waiting for the owner to configure the two
role-specific Admin password variables on the existing demo service; no Render values were changed.

## Environment

- Docker: **UNAVAILABLE** — `docker` executable not installed.
- Node.js: **AVAILABLE** — `v22.23.2`.
- Playwright: **AVAILABLE** — `1.64.0`; browser binaries ran successfully.
- Infrastructure tier: **FALLBACK (embedded/in-memory H2)** — Docker was unavailable; PostgreSQL/Testcontainers
  test skipped.
- Browser tier: **PRIMARY (Playwright)** — desktop Chrome and Pixel 7 projects ran.
- Data strategy: unique randomized Buyer/Seller identities and offerings; local in-memory H2 is discarded
  when the test-managed application exits.

## Journeys exercised

1. Buyer registration without OTP, duplicate refusal, invalid-password rejection, successful login, and
   persisted profile read-back.
2. Seller registration without OTP, pending approval state, protected Seller/Admin endpoints, invalid login,
   Admin approval, and subsequent approved Seller access.
3. Seller creates an offering; Buyer discovers and orders it with a remark; repeated click submits one request;
   inventory reaches sold out; Seller marks it delivered; Buyer history shows delivered status and remark.
4. Mobile Buyer/Seller registration and login; no horizontal overflow at the tested mobile viewport.

The browser suite captures unexpected console errors, uncaught page errors, failed API requests, and unexpected
API responses. A prior targeted attempt observed HTTP 409 from `GET /api/notifications`; two later runs
(targeted lifecycle and complete suite) passed without reproducing it. No root cause is claimed.

## Legacy test assets

- Existing Java JUnit integration/service tests: retained and run with Maven.
- Existing browser E2E suite: none found before this feature branch.
- Migration decision: new Playwright journeys complement the retained Java tests; no existing test was discarded.

## Known gaps

- Native PostgreSQL/Testcontainers integration was skipped because Docker is not installed; H2 does not certify
  PostgreSQL compatibility.
- Recurring/pre-order checkout, occurrence override, cancellation, concurrent final-unit purchase, and broad
  Admin operations were not exercised in the browser suite; related Java tests provide narrower local evidence.
- No live Render Buyer-to-Seller order, delivery, recurring schedule, or Admin operation was submitted.
- Render Admin/Super Admin passwords are not configured as environment variables. Source-controlled fallback
  values were removed; owner configuration is required before live Admin login can be verified safely.
- No real payment was used.

## Issues found

| # | Severity | Description | Disposition |
|---|---|---|---|
| 1 | Medium | One intermediate lifecycle run received HTTP 409 from `GET /api/notifications` and failed the strict browser diagnostic | Not reproduced in the targeted rerun or full browser suite; response-body diagnostics retained for recurrence |
| 2 | High | Admin/Super Admin demo passwords had source-controlled fallbacks while the Render service has no corresponding variables configured | Removed fallbacks from demo/dev property files; deployment/live Admin test blocked pending owner-set Render variables |
