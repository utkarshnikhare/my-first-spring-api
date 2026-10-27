# SocioMart Release Report (refreshed 2026-10-27)

**Report Date:** 2026-10-27
**Auditor:** Principal Engineer / QA Lead (corrective audit)

## 1. Git state

- Branch: main
- Latest commit: f26cd36 (docs(qa): refresh QA reports...)
- Working tree: clean (at audit start)
- Remote: origin https://github.com/utkarshnikhare/my-first-spring-api.git

## 2. CI

- Workflow: .github/workflows/ci.yml
- Latest PR #87: build PASS (2m11s), e2e PASS (9m54s) on 2026-10-27.
- Postgres tests: RUN in CI (Docker available) — 5/5 pass.

## 3. Render

- Service: sociomart-demo (https://sociomart-demo.onrender.com)
- Health check /api/health: {"status":"UP"} (read-only probe succeeded).
- autoDeploy: false (render.yaml). Pushes do NOT auto-deploy.
- Deployed commit SHA: NOT exposed by health endpoint; UNVERIFIED.
- Render API key: not available (RENDER_API_KEY unset). Programmatic deploy/verify not possible.

## 4. Release blockers

1. Manual Render deploy required to publish latest commit (autoDeploy false).
2. Deployed commit SHA must be verified after deploy.
3. Local Postgres tests blocked (no Docker); rely on CI evidence.

## 5. Final status

NO-GO — code/tests pass in CI; production deployment unverified (autoDeploy false, no Render API key, no commit SHA exposed by health endpoint). Manual deploy + SHA verification required.

## 6. Live smoke tests (2026-10-27)

- GET /api/health -> 200 {"status":"UP"}
- GET / -> 200 (3037 bytes)

- Deployed commit SHA: NOT exposed by any endpoint; UNVERIFIED.
- Render deploy: NOT performed (no Render API key / CLI in environment). Manual deploy required.
