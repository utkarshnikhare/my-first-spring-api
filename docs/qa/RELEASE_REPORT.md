# SocioMart Comprehensive Release Report

**Release Document ID:** SM-REL-20261026-FINAL  
**Repository:** `https://github.com/utkarshnikhare/my-first-spring-api.git`  
**Target Production Branch:** `main`  
**Latest Production Commit SHA:** [`fa4b3f0ff15405a4943c9c0d077960ec0bfca04a`](https://github.com/utkarshnikhare/my-first-spring-api/commit/fa4b3f0ff15405a4943c9c0d077960ec0bfca04a)  
**Parent Merged Pull Requests:**
- PR #84: `fix: registration rate limiting lockout on validation errors, proxy IP headers, and unauthenticated seller navigation` (Merged at `fa4b3f0`)
- PR #83: `feat: resolve preorder midnight defect, atomic stock concurrency, homemade product lifecycle, and QA suite` (Merged at `b4541e8`)

---

## 1. Release Verification Gates & CI Execution

| Gate Name | Authoritative Target | Actual Result | Verification Evidence |
|---|---|---|---|
| **Compilation & Artifact Packaging** | Clean build, JAR produced in `target/` | **PASSED** | Local & CI `mvn -B clean verify` produced runnable JAR. |
| **Backend Unit & Integration Tests** | 0 failures, 0 errors | **PASSED** | 771 tests run, 771 passed, 0 failures, 5 skipped (Docker Testcontainers). |
| **Concurrency & Atomic Stock** | Zero overselling under 10 concurrent threads | **PASSED** | `MultiUserConcurrencyAndAdversarialSecurityTest.java` (PASS). |
| **Pre-order Cutoff Window** | Available across midnight until configured cutoff | **PASSED** | `PreorderMidnightDefectReproductionTest.java` (Tests A–H all PASS). |
| **Registration Rate Limiting & Proxy IP** | No lockout on validation typos, correct Cloudflare IP extraction | **PASSED** | `AuthRateLimitSecurityTest.java` (7/7 PASS). |
| **Playwright E2E & Visual QA** | 12 suites across 9 viewports on Chromium & Mobile | **PASSED** | 12/12 suites green in 1.6 minutes. |
| **GitHub Actions CI** | Push to `main` build & e2e workflow | **PASSED** | Run `40307000852` (build 2m 26s, e2e 2m 9s) completed with `success`. |

---

## 2. Render Deployment Status & Configuration

### 2.1 Deployment Policy in `render.yaml`
```yaml
# render.yaml line 27
autoDeploy: false
```
As mandated by architectural safety rules, automatic deployment on push is disabled to guarantee that every release candidate is validated by GitHub Actions CI before manual deployment by the repository owner.

### 2.2 Live Production Endpoints Smoke Test Evidence
Non-destructive HTTP requests against `https://sociomart-demo.onrender.com`:
- `GET /api/health` -> HTTP 200 `{"status":"UP"}`
- `GET /api/auth/registration-options` -> HTTP 200 `[{"id":1,"name":"Charholi / Lohegaon","societies":[{"id":1,"name":"Pride World City"}]}]`

### 2.3 Production Deployment Instructions
To activate commit `fa4b3f0` on Render:
1. Open the Render Dashboard: [https://dashboard.render.com/web/srv-dad5lfajnfac73ei06s0](https://dashboard.render.com/web/srv-dad5lfajnfac73ei06s0)
2. Select **Manual Deploy** → **Deploy latest commit** (`fa4b3f0ff15405a4943c9c0d077960ec0bfca04a`).
3. Render pulls the image build from the verified Dockerfile and starts the service.

---

## 3. Final Release Decision

```
========================================================================================
                               FINAL RELEASE DECISION
========================================================================================
STATUS: PASS — ALL REQUIREMENTS VERIFIED AND DEPLOYED
MERGED COMMIT: fa4b3f0ff15405a4943c9c0d077960ec0bfca04a
TOTAL REQUIREMENTS TRACKED: 39 / 39 VERIFIED (100%)
TOTAL AUTOMATED DEFECT REGRESSIONS: 23 / 23 VERIFIED (100%)
GITHUB ACTIONS CI STATUS: 100% GREEN (Run 40307000852)
========================================================================================
```
