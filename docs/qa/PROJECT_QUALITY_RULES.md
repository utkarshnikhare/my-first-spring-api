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


---

## 14. Adversarial Quality Rules (Addendum 2026-10-27)

The following rules are mandatory for all future SocioMart quality work and supplement, but do not replace, the rules above.

1. Never claim a test passed unless it actually ran and its result was inspected.
2. Never hide failures by weakening assertions, deleting tests, suppressing exceptions, or marking tests skipped.
3. Never treat a successful compilation as proof of functional correctness.
4. Never treat an HTTP 200 response as proof that a business workflow is correct.
5. Never trust client-side validation without verifying server-side enforcement.
6. Never trust a UI restriction without testing the underlying API.
7. Never trust a single-user test for stock, order, payment, or concurrent-update correctness.
8. Never claim PostgreSQL correctness based only on H2 tests.
9. Never mark a defect fixed without a regression test or documented, reproducible verification evidence.
10. Never modify production data or perform destructive security testing against production.
11. Never introduce a new product feature as part of defect repair.
12. Preserve user changes, existing requirements, test evidence, and repository history.
13. Distinguish confirmed defects, suspected defects, environmental limitations, and untested behavior.
14. After each significant repair, actively attempt to reproduce the original defect and discover related failures.
15. After the repair phase, perform an independent adversarial pass as if another engineer's work is being reviewed.

### Evidence requirements for "Verified Fixed"

A defect may be marked **Verified Fixed** only when:
- the original failure was reproduced or otherwise established with credible evidence;
- the root cause was addressed;
- a regression test or documented verification exists;
- the test passed after the repair;
- relevant adversarial retesting did not reproduce the defect;
- related existing workflows were checked.

Otherwise use **Fixed-Unverified**, **Blocked**, or **Open**.

- **Gate A — Functional Correctness:** All critical user journeys (Buyer, Kitchen Seller, Homemade Seller, Mixed Seller, Admin) work end-to-end.
- **Gate B — Security & Authorisation:** Zero unauthorized privilege escalations, IDOR vulnerabilities, or cross-tenant data leaks.
- **Gate C — Data & Transaction Integrity:** Inventory decrements atomically, cancellations restore stock exactly once, and order/payment snapshots remain immutable.
- **Gate D — Responsive UI & Accessibility:** 11 standard viewports (`320px` to `1920px`) render without horizontal overflow, touch targets are `>=44px`, and light/dark themes maintain high contrast.
- **Gate E — Performance & Concurrency:** Real multi-user concurrency and race-condition handling verified under realistic load levels.
- **Gate F — Regression Safety:** Pre-order midnight cutoff, kitchen meal workflows, and existing verified capabilities remain 100% operational.
- **Gate G — Traceable Evidence:** Every claim is backed by executable tests, terminal logs, or inspected database records.
- **Gate H — Release Safety:** Working tree is clean, dependencies are secured, and no breaking schema mutations are introduced without backward compatibility.
