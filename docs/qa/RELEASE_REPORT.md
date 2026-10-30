# SocioMart Release Report (refreshed 2026-10-30)

**Report Date:** 2026-10-30
**Auditor:** Principal Engineer / QA Lead (release and reliability audit)

> Sections 1–7 are a historical snapshot from 2026-10-27. Section 8 is the authoritative current
> disposition; do not use the historical branch, commit, CI, or Render status as current evidence.

## 1. Git state

- Branch: main (synced with origin/main)
- Latest commit: 07f4def8352afff7c7a1c05b113f04017f18bf7f (Merge pull request #90 — includes SM-028 cancelled-order revenue fix + /api/version endpoint)
- Working tree: clean for source code; docs/qa/*.md updated in this pass
- Remote: origin https://github.com/utkarshnikhare/my-first-spring-api.git
- Local main == origin/main == 07f4def
- QA docs branch: `chore/qa-docs-release-report-20261027` pushed to origin at commit `970dbfa` (docs/qa: DEFECT_REGISTER.md, PROJECT_QUALITY_RULES.md, RELEASE_REPORT.md, REQUIREMENTS_TRACEABILITY.md, TEST_EXECUTION_REPORT.md + new ADVERSARIAL_TEST_CHECKLIST.md)
- PR required: create pull request from `chore/qa-docs-release-report-20261027` into `main` via GitHub web UI at https://github.com/utkarshnikhare/my-first-spring-api/pull/new/chore/qa-docs-release-report-20261027 — no GitHub API access available to create/merge PR programmatically.

## 2. CI

- Workflow: .github/workflows/ci.yml
- CI run 40494866078: success (build and e2e).
- CI Maven: 776 tests, 0 failures, 0 errors, 0 skipped.
- Postgres/Testcontainers tests: 5/5 PASS in CI.
- Playwright E2E: 13 passed, 0 failed.

## 3. Render — live verification (2026-10-27)

- Service: sociomart-demo (https://sociomart-demo.onrender.com)
- render.yaml: plan=free, runtime=docker, autoDeploy=false, healthCheckPath=/api/health
- autoDeploy: false — pushes do NOT auto-deploy; manual deploy required.
- **Live app IS reachable** (woke from cold start):
  - `GET /api/health` → 200 `{"status":"UP"}` — application is healthy
  - `GET /api/version` → 200, publicly accessible (no 401/403):
    ```json
    {"application":"SocioMart","version":"0.0.1-SNAPSHOT","commit":"unknown","shortCommit":"unknown","buildTime":"2026-10-27T11:26:08.886Z"}
    ```
    - **`commit: "unknown"`** — the deployed build does NOT report an approved commit SHA.
    - The `git-commit-id-maven-plugin` is configured in `pom.xml` for commit `07f4def`, which would populate `GitProperties` and report the actual commit SHA. Since it reports "unknown", the deployed build is **not** from commit `07f4def8352afff7c7a1c05b113f04017f18bf7f`.
    - A `"commit": "unknown"` response does NOT by itself prove old code, but it confirms the deployed build lacks git commit metadata that the approved commit would provide.
  - `GET /` → 200, homepage loads ("SocioMart — Discover. Connect.")
- Render API key: not available (RENDER_API_KEY unset). Programmatic deploy, log retrieval, and SHA verification not possible from this environment.

**Conclusion:** The live app is running, but `/api/version` reports `"commit": "unknown"` instead of `07f4def...`. The deployed commit SHA does NOT match the approved commit `07f4def8352afff7c7a1c05b113f04017f18bf7f`. Manual deployment of commit `07f4def` via the Render dashboard is required to publish the approved release.

## 4. Manual Render deployment steps (approved commit 07f4def)

Because autoDeploy is false and no Render API key/CLI is available in this environment, deployment must be done manually via the Render web dashboard:

1. Open https://dashboard.render.com and log in.
2. Select the **sociomart-demo** web service (type: web, runtime: docker, plan: free).
3. Go to the **Deploys** tab (or "Manual Deploy" section).
4. Trigger a **manual deploy** of the latest commit on the `main` branch.
   - The commit to deploy must be `07f4def8352afff7c7a1c05b113f04017f18bf7f`.
   - Render will build the Docker image from `./Dockerfile` and restart the service.
5. Wait for the build to complete and the service to become "live" (Render free tier may take 60-90s on cold start after a deploy).
6. Note: changing render.yaml does not alter the running deployment; it only governs future deploys.

## 5. Post-deploy smoke test checklist (required to move past PARTIALLY VERIFIED)

All checks against https://sociomart-demo.onrender.com:

| # | Endpoint | Expected | Non-destructive? |
|---|----------|----------|------------------|
| 1 | GET /api/health | 200 `{"status":"UP"}` | Yes (read-only probe) |
| 2 | GET /api/version | 200; body reports build commit SHA `07f4def...`; publicly accessible (no 401/403) | Yes (read-only metadata) |
| 3 | GET / | 200 (homepage loads) | Yes |
| 4 | Buyer workflow (read-only) | e.g. GET product listing / discovery — 200 | Yes (no state change) |
| 5 | Seller workflow (read-only) | e.g. GET seller dashboard summary — 200 or auth-required redirect | Yes (no state change) |
| 6 | Admin workflow (read-only) | e.g. GET admin dashboard — 200 or auth-required redirect | Yes (no state change) |

**Deployed commit SHA verification:** Compare the commit SHA reported by `/api/version` against `07f4def8352afff7c7a1c05b113f04017f18bf7f`. They must match.

## 6. Release blockers

1. Manual Render deploy required to publish commit 07f4def (autoDeploy false).
   - Live app currently running with `commit: "unknown"` — NOT the approved commit 07f4def.
2. Deployed commit SHA must be verified via `/api/version` after deploy.
   - Must report `07f4def8352afff7c7a1c05b113f04017f18bf7f` (or at minimum not "unknown").
3. No Render API key/CLI in this environment — deploy and smoke tests must be performed by someone with Render dashboard access.
4. Local Postgres tests blocked (no Docker); rely on CI evidence (5/5 PASS).

## 7. Final status

**PARTIALLY VERIFIED** — code and tests pass in CI (776/0/0), approved commit 07f4def is merged to main and CI-green, QA docs branch pushed to GitHub (`chore/qa-docs-release-report-20261027` at `4b238a4`), but the Render deployment has NOT been confirmed to run 07f4def.

Live verification results (2026-10-27):
- `GET /api/health` → 200 `{"status":"UP"}` ✅
- `GET /api/version` → 200, public, but `"commit": "unknown"` ❌ (expected: `07f4def...`)
- `GET /` → 200, homepage loads ✅

Release cannot be marked fully verified until:
- (a) commit 07f4def is manually deployed to Render via dashboard, and
- (b) `/api/health`, `/api/version` (commit SHA = `07f4def...`), homepage, and non-destructive buyer/seller/admin smoke tests all pass in production.

No new features and no unrelated code changes were introduced in this pass.

## 8. Current Release Disposition (2026-10-30)

**Status: CODE MERGED AND CI-VERIFIED; PRODUCTION RELEASE BLOCKED.** The current code is on `main`,
but it has not been deployed to Render. No production query, migration, or deployment was executed.

### GitHub and CI

- Pull request [#96](https://github.com/utkarshnikhare/my-first-spring-api/pull/96) merged successfully.
- Exact merge commit on `main`: `7220f6c241585cb2094f20c11c49f632cdd3d36e`.
- GitHub Actions run [41048205309](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/41048205309) completed successfully for that exact SHA; both `build` and `e2e` jobs passed.
- Earlier conflicting PR #94 was closed as superseded by #96; its branch was left unchanged.

### Local Verification

| Check | Result |
|---|---|
| `.\mvnw.cmd -B clean verify` | PASS — 812 tests, 0 failures, 0 errors, 5 Docker-dependent skips |
| `npm test` (`my-first-spring-api/e2e`) | PASS — 13/13 Playwright tests |
| `npm audit --audit-level=high` | PASS — 0 vulnerabilities in the E2E dependency tree |
| `node --check` (`buyer.js`, `seller.js`) | PASS |
| `git diff --check` | PASS |

The five local skips require Docker/PostgreSQL Testcontainers. The GitHub CI build and E2E jobs both
passed on the merged commit.

### Render and Production Data

- Service: [sociomart-demo](https://sociomart-demo.onrender.com); Render auto-deploy is **Off**.
- Dashboard-reported live commit: `76c11a45f7ac37c552fd570519608fd96840f084`.
- Latest failed deployment: `dep-dbi4qu5g1s2s73e0h7i0`, attempted commit
  `dd0807eecdefe1e8acc63300dcf6ddb522aecfe3`; startup failed because
  `orders.payment_method` was absent.
- Latest read-only requests to `/`, `/api/health`, and `/api/version` each timed out after 30 seconds
  with no response bytes. Production health and deployed version are **NOT VERIFIED**.
- Render configuration has `SPRING_FLYWAY_ENABLED=true` and
  `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`. The actual production Flyway history and schema have not
  been inspected; do not change these settings or deploy to bypass that verification.
- V17 is an additive, forward-only repair migration on `main`. It has not been applied to production.
- Production database identity, schema/history, record counts, existing-data integrity, backup
  configuration, and isolated backup restoration are **NOT VERIFIED**. No production data was changed.

### Remaining Production Gate

Before any manual deployment of `7220f6c241585cb2094f20c11c49f632cdd3d36e`, an authorized operator must
verify the target database identity, Flyway history and core-table record counts using read-only access;
confirm an owner-approved backup and a successful isolated restore; and then verify the exact deployed
commit, startup logs, health/version endpoints, database connectivity, and non-destructive workflows.
Keep auto-deploy Off. No production readiness or zero-defect claim is made.

## 9. Production Gate Recheck (2026-10-30)

**Disposition: NO-GO; production safety gates remain open.** Evidence below was read from the
authenticated Render dashboard and public endpoints. No secrets or database credentials were revealed.

| Gate | Status | Evidence |
|---|---|---|
| Correct Render service and source | **PASS** | Service `sociomart-demo` (`srv-dad5lfajnfac73ei06s0`) is linked to `utkarshnikhare/my-first-spring-api`, branch `main`. |
| Auto-deploy disabled | **PASS** | Render Settings shows Auto-Deploy Off. No setting was changed. |
| Actual currently selected live commit | **PASS** | Render dashboard identifies successful live deployment `dep-dbg9r0ivcj2c73bjg8ng`, full SHA `76c11a45f7ac37c552fd570519608fd96840f084`. This is not the verified release SHA. |
| `/api/version` commit discrepancy | **PASS** | Cause identified: endpoint returns `unknown` because live SHA `76c11a4` predates the `RENDER_GIT_COMMIT` fallback and its Docker build did not expose Git metadata. Current `main` has the validated runtime fallback, but that fix is not live. Render deployment metadata is authoritative for the old deployment. |
| Failed deployment root cause | **FAIL** | Deployment `dep-dbi4qu5g1s2s73e0h7i0` for `dd0807eecdefe1e8acc63300dcf6ddb522aecfe3` failed Hibernate schema validation: `orders.payment_method` was missing. |
| Current main deployment | **BLOCKED** | Manual attempt `dep-dbi8k8psrm7s73dic5f0` for `fee5592fe8f8f6cd0dfc608e6be986027a0cbc3c` was canceled before it became live because database-history and backup gates were not met. Build completed and startup logs show a PostgreSQL connection was opened; no app-ready or Flyway migration result appears in the available log window. Whether startup performed any schema action is separately **NOT VERIFIED**; read-only history/status comparison is required before proceeding. |
| Production database identity | **BLOCKED** | A prior successful deployment log identifies Supabase PostgreSQL 17.11, database `postgres`, schema `public`. This is historical runtime evidence, not a current read-only confirmation of the target identity/configuration. The Supabase project console redirected to sign-in; no authenticated provider session or separate read-only DB connection was available. No database connection environment variables are present in the audit environment. |
| Schema at failed deployment | **FAIL** | Hibernate reported missing `orders.payment_method`. |
| Complete current Flyway history/schema | **NOT VERIFIED** | The prior live deployment logged schema version 13 and validation of 14 migrations. That is not a complete current history dump. V14–V17 have not been independently checked against the live database. `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` remains configured and unchanged. |
| Backup availability and isolated restore | **BLOCKED** | The Supabase project console requires sign-in; no provider backup artifact/policy or approved isolated restore target was accessible. No backup or restore was attempted. |
| Existing record counts/data integrity | **NOT VERIFIED** | No before/after counts or read-only integrity queries were available. Do not claim production records remained unchanged based on deployment status alone. |
| Current HTTP health and public endpoints | **PASS** | After the Render free instance woke, `/api/health`, `/`, `/api/version`, `/api/marketplace`, `/api/discovery/counts`, `/api/kitchens`, and `/api/auth/config` all returned HTTP 200. Health body was only `{"status":"UP"}`; it does not prove database readiness. Public discovery returned `live=0`, `tomorrow=0`, `preorder=0`, `all=5`. |
| Current deployed version endpoint | **FAIL** | `/api/version` returned `commit=unknown`, `shortCommit=unknown`, build time `2026-10-27T11:26:08.886Z`; Render metadata identifies the running deployment as SHA `76c11a45f7ac37c552fd570519608fd96840f084`. |
| Anonymous access to protected read APIs | **PASS** | Unauthenticated `GET` requests to `/api/seller-app/dashboard`, `/api/seller/products`, `/api/buyer/orders/my`, `/api/admin/dashboard`, and `/api/admin/system-health` all returned HTTP 401. This verifies anonymous denial only; it does not verify valid buyer/seller/admin sessions. |
| Database readiness, authenticated workflows, and production data integrity | **NOT VERIFIED** | The old live health endpoint only returns `status=UP`; database identity/history/record counts and authenticated buyer/seller/order/payment workflows remain unverified. No authenticated write or payment operation was attempted. |
| Authenticated buyer/seller/order/payment workflows | **NOT TESTED** | No test account or safe authenticated production smoke-test authorization was available. No writes or payment attempts were made. |
| Exact release SHA deployed and verified | **BLOCKED** | GitHub main and CI verify `fee5592fe8f8f6cd0dfc608e6be986027a0cbc3c`; Render still reports the selected live SHA as `76c11a45f7ac37c552fd570519608fd96840f084`. The `fee5592` attempt is canceled, not deployed. |

### Remaining actions required to clear NO-GO

1. Provide an approved, secure **read-only** connection to the production database (without sending
   credentials in chat) so an operator can confirm database identity, complete `flyway_schema_history`,
   relevant schema/constraints, and baseline record/integrity counts.
2. Confirm an owner-approved backup and restore it into an already approved isolated target. Verify
   Flyway history, schema, and representative records there. Do not create billable resources or run
   restore/write tests without owner approval.
3. Reconcile the current production schema with V14–V17 and determine whether the canceled startup
   changed schema state; compare record counts before any new deploy.
4. Only after gates 1–3 pass, manually deploy the exact CI-verified main SHA with Auto-Deploy Off.
   Monitor logs, confirm Flyway and application readiness, `/api/version` SHA, database readiness, and
   non-destructive smoke tests. Verify existing-record counts remain unchanged.

Until then, do not retry deployment or claim a verified production release.

### Access and follow-up evidence (2026-10-30 17:57–18:02 +05:30)

- Render dashboard access is available and confirms service `srv-dad5lfajnfac73ei06s0` remains linked to
  `main`; the live deployment still points to `76c11a45f7ac37c552fd570519608fd96840f084`. Settings
  continue to show Auto-Deploy Off.
- The attempted `fee5592` deployment is **Canceled**, not live. Its logs show a PostgreSQL connection
  was established during startup, but the available log window ends before any Flyway validation,
  migration result, or application-ready message. Do not infer that the database was untouched.
- The Supabase project URL resolves to a sign-in page. The audit environment has no Render API key or
  `SOCIOMART_DB_JDBC_URL`, username, or password variables. No database credentials were exposed or
  queried. The user was asked to provide approved secure read-only database/backup access; no access
  response was available in this run.
- The initial requests timed out during Render free-instance wake. After the service woke, a subsequent
  read-only pass returned HTTP 200 for `/`, `/api/health`, `/api/version`, `/api/marketplace`,
  `/api/discovery/counts`, `/api/kitchens`, and `/api/auth/config`. `/api/health` returned only
  `{"status":"UP"}`; database readiness is not thereby verified. `/api/version` still returned
  `unknown` despite Render metadata identifying live SHA `76c11a45f7ac37c552fd570519608fd96840f084`.
- Anonymous GETs to the seller dashboard, seller products, buyer orders, admin dashboard, and admin
  system-health routes each returned 401. No authenticated workflow, write operation, order, or payment
  was attempted.
- Evidence update PR [#100](https://github.com/utkarshnikhare/my-first-spring-api/pull/100), head
  `42ba704528caee44effe6b6a5309e01288fbf4ae`, passed both GitHub Actions jobs. It remains an open
  documentation PR; production remains NO-GO.
