# SocioMart — Production Deployment Runbook & Operational Guide

This document details the deployment procedures, environment settings, and rollback instructions for SocioMart across Netlify (Frontend) and Render (Backend).

---

## 1. Environments Overview

| Environment | Hosting Provider | Target Service / Site | Production URL |
| :--- | :--- | :--- | :--- |
| **Frontend** | Netlify | `sociomart-frontend` (ID: `3a1f209c-ac6c-47de-a786-938ee623735b`) | `https://sociomart-frontend.netlify.app` |
| **Backend** | Render | `sociomart-demo` (ID: `srv-dad5lfajnfac73ei06s0`) | `https://sociomart-demo.onrender.com` |
| **Database** | Supabase | Managed PostgreSQL 16 (Tokyo `ap-northeast-1`) | Dedicated Connection Pool |

---

## 2. Production Deployment Procedures

### A. Netlify Frontend Deployment
The frontend static assets are published directly from `my-first-spring-api/src/main/resources/static`.

#### Manual Deployment via Netlify CLI:
```bash
# Navigate to repository root
cd SocioMart

# Deploy to Netlify production
npx netlify-cli deploy --dir=my-first-spring-api/src/main/resources/static --prod --site=3a1f209c-ac6c-47de-a786-938ee623735b
```

#### Verification Steps:
1. Verify `https://sociomart-frontend.netlify.app/` returns HTTP 200 with the latest `CACHE_VERSION` in `sw.js`.
2. Confirm `/api/health` returns `{"status":"UP"}` via the Netlify transparent proxy.
3. Check mobile navigation clearance (360px & 390px viewports) to ensure no floating elements obstruct bottom navigation.

---

### B. Render Backend Deployment
Render hosts the Java Spring Boot Docker container. Auto-deploy is **deliberately disabled** (`autoDeploy: false`) so that pushes to GitHub do not trigger unverified deployments.

#### Manual Deployment via Render API:
Deploy the approved merge commit SHA using the Render API:
```bash
curl -X POST "https://api.render.com/v1/services/srv-dad5lfajnfac73ei06s0/deploys" \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -d '{
    "clearCache": "do_not_clear",
    "commitId": "<APPROVED_COMMIT_SHA>"
  }'
```

#### Verification Steps:
1. Check backend health: `GET https://sociomart-demo.onrender.com/api/health` -> HTTP 200 `{"status":"UP"}`.
2. Check version endpoint: `GET https://sociomart-demo.onrender.com/api/version` -> matches `<APPROVED_COMMIT_SHA>`.
3. Check application startup logs in Render dashboard to ensure HikariCP connection pool initialized cleanly and Flyway validated all migrations without drift.

---

## 3. Rollback Procedures

### Frontend Rollback (Netlify)
If a critical regression occurs in the web UI:
1. Open the Netlify Deploys dashboard: `https://app.netlify.com/projects/sociomart-frontend/deploys`.
2. Identify the previous stable deployment.
3. Click **Publish deploy** to instantly roll back production traffic to the previous release.

### Backend Rollback (Render)
If a backend defect occurs:
1. Execute a deploy API request targeting the previous stable commit SHA:
   ```bash
   curl -X POST "https://api.render.com/v1/services/srv-dad5lfajnfac73ei06s0/deploys" \
     -H "Authorization: Bearer $RENDER_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"commitId": "<PREVIOUS_STABLE_SHA>"}'
   ```
2. Verify startup logs until the previous container is running and healthy.
