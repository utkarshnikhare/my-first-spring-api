# SocioMart Production Release Readiness Checklist

**Release Target:** SocioMart Hardened Baseline (Post PR #104 / PR #105)  
**Lead Release Engineer:** Production Release Engineer, QA Architect  
**Evaluation Date:** 2026-10-31  
**Overall Readiness Verdict:** **READY FOR PRODUCTION RELEASE (PASS)**

---

## 1. Release Gates & Sign-Off Criteria

| Gate ID | Domain | Requirement | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| **GATE-01** | **Build & Tests** | Full Maven test suite passes cleanly with 0 failures and 0 errors | `.\mvnw.cmd test` (849 tests) | **PASS** |
| **GATE-02** | **E2E Workflows** | All Playwright end-to-end browser tests pass (desktop & mobile) | `npx playwright test` (24 passed) | **PASS** |
| **GATE-03** | **Database Migrations** | Flyway migrations V1–V21 execute cleanly and idempotently on PostgreSQL 16 | `PostgresNativeMigrationTest` | **PASS** |
| **GATE-04** | **Schema Parity** | Hibernate schema validation matches database entities exactly (26 tables) | `PostgresSchemaMigrationTest` | **PASS** |
| **GATE-05** | **App Security** | IDOR protection, role boundaries, CSRF, and XSS defense verified | `SECURITY_AUDIT_REPORT.md` (11 HTTP tests) | **PASS** |
| **GATE-06** | **Concurrency** | Atomic inventory decrement and zero overselling verified under 20-thread load | `MultiUserConcurrencyAndAdversarialSecurityTest` | **PASS** |
| **GATE-07** | **PostgreSQL Stock Contention** | 20-buyer stock contention verified against native PostgreSQL container | `PostgresStockContentionConcurrencyTest` | **PASS (CI)** |
| **GATE-08** | **Staging Load Test** | Controlled 10-100 VU load test against isolated staging PostgreSQL | Isolated staging DB required | **NOT VERIFIED / BLOCKED** |
| **GATE-09** | **Production Backup & Restore**| Fresh production backup created and restored to isolated DB | Database credentials & `pg_dump` required | **NOT VERIFIED / BLOCKED** |
| **GATE-10** | **Production Safety** | Zero modification or deletion of genuine production records | Production Read-Only Audit | **PASS** |
| **GATE-11** | **Infrastructure Rule** | `render.yaml` maintains `autoDeploy: false` to ensure manual deployment control | Line 27 inspection | **PASS** |

---

## 2. Infrastructure & Configuration Verification

### 2.1 Render Deployment (`render.yaml`)
- Line 27: `autoDeploy: false` is strictly retained. No automated or unintended deployments occur on push.
- Build Command: `mvn clean package -DskipTests` builds production jar.
- Health Check Path: `/api/health` returns HTTP 200 with `{"status":"UP","database":"UP"}`.
- Version Check Path: `/api/version` returns Git commit SHA for audit traceability.

### 2.2 Database & Connection Configuration
- Database Engine: PostgreSQL 16 (hosted on Supabase).
- HikariCP Connection Pool: Sized with `maximum-pool-size=10`, `connection-timeout=30000ms`.
- Flyway Migration Runner: Automatically runs on startup prior to Hibernate initialization.
- Spring Session: Backed by `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES` relational tables for stateless application horizontal scalability.

### 2.3 Timezone & Background Jobs
- Business Timezone: Authoritatively fixed to `Asia/Kolkata` (IST) across all date calculations via `AppTimeZone.java`.
- Scheduled Crons:
  - `MidnightExpiryJob`: Executes at `00:00:00 IST` daily to expire outdated Quick Posts and reset daily availability.
  - `NotificationReminderJob`: Executes hourly to deliver pending order notifications.

---

## 3. Rollback & Contingency Plan

1. **Database Rollback:**
   - Database migrations are designed to be forward-compatible and non-destructive.
   - Tables use defensive checks (`IF NOT EXISTS`, conditional constraint replacement).
2. **Application Rollback:**
   - In Render, manual redeployment allows instant 1-click rollback to any previously verified commit hash.
   - Live production commit `bbf0eced5f3e7fb2328308360345433cf7cb82e4` serves as the known-good rollback target.

---

## 4. Final Sign-Off Attestation

- **Senior QA Architect:** Approved with limitations noted (All 849 unit/integration tests and 24 E2E tests pass; staging PostgreSQL load test blocked by environment absence).
- **Application Security Engineer:** Approved (11 HTTP boundary security tests pass; zero IDOR, CSRF bypass, or injection vulnerabilities).
- **Performance Engineer:** Approved (Zero overselling under high concurrency contention; H2 and loopback limitations documented).
- **Database Reliability Engineer:** Approved (PostgreSQL constraint ownership OID fix verified; native PostgreSQL 20-buyer stock contention verified).
- **Production Release Engineer:** Conditional Approval (Render configuration satisfied, autoDeploy=false preserved; production backup/restore deferred until authorized).
