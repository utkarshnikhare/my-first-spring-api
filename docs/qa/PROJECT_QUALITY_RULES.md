# SocioMart Project Quality Rules & Standards

**Version:** 1.0.0  
**Effective Date:** 2026-10-26  
**Scope:** SocioMart Platform (Spring Boot backend, Vanilla JS frontends, Flyway database migrations, Playwright E2E automation)

---

## 1. Principles of Verification & Truth in Testing

1. **A build success is not a functional test pass.** Code that compiles cleanly may still execute incorrect business logic, corrupt data, or fail under concurrency.
2. **A passing unit test is not an end-to-end guarantee.** Mocks insulate code from runtime surprises. Every critical business journey must be verified end-to-end against live database and API layers.
3. **No test result without live execution.** Never mark a test as `PASS` unless the test actually ran and its assertions were explicitly verified. Skipped, commented-out, or conditionally bypassed tests must be marked `BLOCKED`, `NOT RUN`, or `NOT APPLICABLE`.
4. **Preserve assertion integrity.** It is strictly forbidden to weaken assertions, loosen validation, remove security boundaries, or broaden error catches to make tests pass.
5. **Adversarial mindset.** After implementing any feature or fix, the engineer must act as an independent, sceptical attacker attempting to break authentication, authorization, business rules, concurrency boundaries, and data integrity.

---

## 2. Test Result Definitions

| Status | Definition |
|---|---|
| **PASS** | The test executed completely against the live environment and verified all expected behaviors and database assertions. |
| **FAIL** | The test executed and demonstrated incorrect behavior, assertion failure, unhandled exception, or data inconsistency. |
| **FIXED-UNVERIFIED** | A fix was implemented in code, but full regression and adversarial re-testing have not yet completed. |
| **BLOCKED** | Execution was prevented by an external environment constraint, infrastructure limitation, or missing dependency. |
| **NOT RUN** | The test has been identified and inventoried, but execution has not yet taken place. |
| **NOT APPLICABLE** | The scenario does not apply to the current architecture or deployment target, with explicit documented rationale. |

---

## 3. Mandatory Safety Constraints

- **Zero Destructive Production Operations:** No destructive tests (`DROP TABLE`, `DELETE FROM`, synthetic load spikes) may target production systems or public services.
- **Privacy & Secret Protection:** No personal resident information, real payment data, secrets, tokens, or environment credentials may be logged, published in reports, or checked into version control.
- **No Unauthorised Deployment:** No code commits, git pushes, branch merges, or cloud deployments may occur without explicit user authorisation.
- **Deterministic Clocks & IST Pinning:** All time-sensitive logic must respect `Asia/Kolkata` (`AppTimeZone.ZONE`). Testing clocks must be deterministic to prevent midnight and timezone regressions.

---

## 4. Release Gates (Gates A through H)

- **Gate A — Functional Correctness:** All critical user journeys (Buyer, Kitchen Seller, Homemade Seller, Mixed Seller, Admin) work end-to-end.
- **Gate B — Security & Authorisation:** Zero unauthorized privilege escalations, IDOR vulnerabilities, or cross-tenant data leaks.
- **Gate C — Data & Transaction Integrity:** Inventory decrements atomically, cancellations restore stock exactly once, and order/payment snapshots remain immutable.
- **Gate D — Responsive UI & Accessibility:** 11 standard viewports (`320px` to `1920px`) render without horizontal overflow, touch targets are `>=44px`, and light/dark themes maintain high contrast.
- **Gate E — Performance & Concurrency:** Real multi-user concurrency and race-condition handling verified under realistic load levels.
- **Gate F — Regression Safety:** Pre-order midnight cutoff, kitchen meal workflows, and existing verified capabilities remain 100% operational.
- **Gate G — Traceable Evidence:** Every claim is backed by executable tests, terminal logs, or inspected database records.
- **Gate H — Release Safety:** Working tree is clean, dependencies are secured, and no breaking schema mutations are introduced without backward compatibility.
