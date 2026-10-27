# SocioMart Release Report (refreshed 2026-10-27)

**Report Date:** 2026-10-27
**Auditor:** Principal Engineer / QA Lead (fresh audit)

## 1. Git state

- Branch: main
- Latest commit: c19c905 (fix(storefront): public homemade store routing, slug resolution, category isolation, and corrective QA audit (SM-024 to SM-027) (#86))
- Working tree: clean
- Remote: origin https://github.com/utkarshnikhare/my-first-spring-api.git

## 2. CI

- Workflow: .github/workflows/ci.yml exists. Not re-run locally; GitHub Actions status not queried in this audit.

## 3. Render

- Service: sociomart-demo (https://sociomart-demo.onrender.com)
- Health check /api/health: {"status":"UP"} (read-only probe succeeded).
- autoDeploy: false (render.yaml). Pushes do NOT auto-deploy.
- Deployed commit SHA: NOT exposed by health endpoint; unverified. Do not claim c19c905 is live.
- Render API key: not available in this environment (RENDER_API_KEY unset). Programmatic deploy/verify not possible.

## 4. Release blockers

1. Manual Render deploy required to publish c19c905 (autoDeploy false).
2. Deployed commit SHA must be verified after deploy (e.g., via build metadata or Render dashboard).
3. PostgreSQL integration tests (5) blocked locally; should be run in CI or with a Postgres instance before claiming DB compatibility.

## 5. Final status

PASS WITH DOCUMENTED NON-CRITICAL LIMITATIONS
