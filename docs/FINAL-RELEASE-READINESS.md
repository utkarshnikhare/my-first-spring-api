# SocioMart Final Release Readiness

Status snapshot: 2026-10-09. This is a demo release, not a real-customer production launch. The owner
explicitly authorized losing disposable non-seed records in the existing in-memory H2 demo database.

## Readiness summary

| Area | Status | Evidence / boundary |
|---|---|---|
| Recurring Offerings V2 | PASS (local automated) | Recurrence, date overrides, order separation, cutoffs and history covered by the full Maven suite |
| Seller Dashboard V3 | PASS (local automated/UI) | Dashboard script/UI tests and responsive browser checks; live LIVE/RECURRING tabs loaded |
| Full local verification | PASS | `.\mvnw.cmd -B clean verify`: 589 tests, 0 failures, 0 errors, 1 Docker-gated skip |
| GitHub `main` CI | PASS | [Run 37972517277](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37972517277), latest `main` merge commit `39116ce55273cc0ae5174173a3f89254a4bf2039` |
| Existing Render demo | PASS | `sociomart-demo`, Free plan, deployment `dep-db4i1rqj9qps73alfiug` |
| GitHub/Render runtime commit | PASS / synchronized | Runtime source on `main` and Render remain at `d9bb8782ff58719a68df01236c09e9e0990c5bf4`; PR #15 advanced `main` with documentation only |
| Buyer pre-order discovery | LIVE SMOKE | UI and API exposed one preorder occurrence for 2026-10-12; no order was created |
| Buyer occurrence order lifecycle on Render | NOT VERIFIED | Live writes were intentionally avoided; local integration tests passed |
| Admin live operations | NOT VERIFIED | Admin entry route loaded, but no authorized Admin session was used |
| Seller onboarding V1 | PARTIAL / BLOCKED | No OTP registration, complete pending-application workflow, or reserved slug URL; auth migration is out of scope |
| Database / persistence | DEMO LIMITATION | Render uses in-memory H2; non-seed state is disposable and is not guaranteed to survive restart |
| PostgreSQL profile | PREPARED, NOT DEPLOYED | No provider or PostgreSQL resource provisioned |
| ₹0/month infrastructure | PASS | Existing Render Free plan; no paid service/add-on/database enabled |
| Real-user production readiness | NOT READY | Demo authentication/payments, persistence, recovery and live operational validation are not production-grade |

## Deployment evidence

Render dashboard identified the existing `sociomart-demo` service (`srv-dad5lfajnfac73ei06s0`) on the Free
plan. The last successful deployment is `dep-db4i1rqj9qps73alfiug`, for
`d9bb8782ff58719a68df01236c09e9e0990c5bf4`. It runs the `demo` profile with in-memory H2. The owner
authorized loss of disposable non-seed demo rows, and seeded kitchens were available after deployment.

Read-only checks returned HTTP 200 from `/api/kitchens` and `/api/auth/config`. The Buyer preorder search
and matching discovery API both exposed one dated occurrence. Seller LIVE and RECURRING tabs loaded; the
seeded seller had no recurring schedule. The Admin sign-in route loaded, but Admin workflows were not tested.
No live order, schedule mutation, or payment transaction was submitted.

Documentation PR #15 is merged as `39116ce55273cc0ae5174173a3f89254a4bf2039`, and CI passed on `main`.
That merge changed documentation only; Render remains at runtime commit `d9bb8782ff58719a68df01236c09e9e0990c5bf4`,
so no redeploy was required.

## Acceptance boundary

The [requirements audit](./FINAL-REQUIREMENTS-AUDIT.md) reports source counts, overlap decisions, per-area
evidence, and the unverified/blocked criteria. This release is **PARTIAL** against all seven supplied
specifications: local V2/V3 and most operational workflows have automated evidence, but Seller Onboarding V1
and live end-to-end order/Admin acceptance are not complete.

No paid infrastructure or production database/authentication migration is authorized by this release.
