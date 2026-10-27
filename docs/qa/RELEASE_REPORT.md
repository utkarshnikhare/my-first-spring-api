# SocioMart Release Report (refreshed 2026-10-27)

**Report Date:** 2026-10-27
**Auditor:** Principal Engineer / QA Lead (corrective audit)

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
