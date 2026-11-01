# SocioMart QA, Security & Performance Engineering Rules

**Effective Date:** 2026-10-31  
**Authority:** QA Architect, Principal Software Engineer, AppSec Engineer, Release Engineer  
**Scope:** Whole SocioMart Application (Spring Boot backend, Static/PWA frontend, PostgreSQL database, GitHub Actions CI, Render Deployment)

---

## 1. Mandatory Core Principles

### Rule 1: Existing Functionality Must Be Preserved
- All testing and hardening activities operate on the existing codebase without unnecessary rewrites.
- Do not rebuild working features, alter UX/UI styles arbitrarily, remove established functionality, or change established business logic without explicit evidence and approval.
- Preserve backward-compatibility for all database entities, migrations, and APIs.

### Rule 2: Evidence Before Conclusions
- Every discovered defect must be logged in `docs/DEEP_QA_DEFECT_REGISTER.md` with:
  1. Unique Defect ID (e.g., `DEF-SEC-01`, `DEF-CONC-01`).
  2. Affected module and endpoint/view.
  3. Severity (`P0` Critical, `P1` Major, `P2` Important, `P3` Minor).
  4. Exact reproduction steps.
  5. Expected vs. actual behavior.
  6. Request/response telemetry and logs.
  7. Root cause analysis.
  8. Minimal safe fix.
  9. Automated regression test.
- No defect is considered resolved without a passing automated regression test.

### Rule 3: Every Fix Must Be Attacked
- After applying any fix:
  1. Execute the failing scenario to prove the defect was addressed.
  2. Execute new automated regression tests.
  3. Probe with boundary values, nulls, empty inputs, and oversized payloads.
  4. Perform negative authorization and IDOR attack probes.
  5. Test concurrent execution where shared resources/locks are involved.
  6. Run the complete test suite to guarantee zero regression on existing capabilities.

### Rule 4: No Production Damage
- The live production database and genuine user/seller/order data must remain protected at all times.
- Never execute destructive DDL/DML (`DROP`, `TRUNCATE`, bulk unconstrained `DELETE`).
- Never reset production database schemas or manipulate Flyway historical records.
- Never run high-concurrency load tests or destructive penetration tests against the live production host (`sociomart-demo.onrender.com`).
- All aggressive testing and state mutation must use isolated in-memory or Testcontainers PostgreSQL environments.

### Rule 5: No False Confidence
- Status classifications are strictly:
  - `PASS`: Directly tested and verified with reproducible automated or recorded telemetry.
  - `FAIL`: Defect reproduced with failing evidence.
  - `BLOCKED`: Infrastructure or prerequisite unavailable.
  - `NOT TESTED`: Not yet evaluated.
  - `NOT VERIFIED`: Claims without independent test execution.
- An HTTP 200 OK status alone does not prove business correctness.
- Passing a mocked unit test does not prove an entire multi-user flow works.

---

## 2. Test Execution & Verification Levels

1. **Unit Testing**: Tests domain invariants, state machines, timing calculations, and validation rules in isolation.
2. **Integration Testing**: Tests Spring MVC controllers, security filters, transactional service boundaries, and JPA persistence.
3. **PostgreSQL-Native Testing**: Tests Flyway migrations, PostgreSQL-specific DDL/DML, and constraints against native PostgreSQL 16.
4. **Security Testing**: Probes authentication bypass, IDOR, privilege escalation, XSS, CSRF, and injection vulnerabilities.
5. **Concurrency & Load Testing**: Probes stock decrement contention, race conditions, replay submissions, and latency under simulated load.
6. **Browser / E2E Testing**: Validates end-to-end workflows via Playwright across desktop and mobile form factors.
