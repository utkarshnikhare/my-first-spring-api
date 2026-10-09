# SocioMart Final Release Readiness

Status snapshot: 2026-10-09. This release concerns the existing demo only; it is not authorization for a
production migration, paid service, database reset or live restart.

## Readiness summary

| Area | Status | Evidence / boundary |
|---|---|---|
| V2/V3 implementation | PASS (automated) | Detailed mapping in [acceptance matrix](./FINAL-V2-V3-ACCEPTANCE-MATRIX.md) |
| Local automated regression | PASS | `mvnw -B clean verify`: 589 tests, 0 failures, 0 errors, 1 Docker-gated skip |
| PR #13 GitHub CI | PASS | PR check run 37962316067 passed on `b1dc83a`; the PR was merged |
| Latest GitHub main CI | PASS | Push run 37962564417 succeeded on merge commit `58932f5` |
| Existing Render service / health | LIVE SMOKE | Existing service and old deployed commit observed; `GET /api/kitchens` returned HTTP 200 |
| Newer main vs Render | DIFFERENT | Main includes PostgreSQL profile/migration preparation; Render remains on PR #7 runtime commit |
| Live Buyer occurrence flow | NOT TESTED | No live preorder item; public writes avoided |
| Live Admin workflows | NOT TESTED | No authorized Admin browser session |
| Database preservation | BLOCKED | In-memory H2 export/restore not verified |
| Persistent PostgreSQL | PREPARED, NOT DEPLOYED | Flyway profile and tests exist; no provider selected or provisioned |
| Authentication readiness | NOT READY FOR REAL USERS | Demo-only mobile-number session login |
| Payment readiness | NOT READY FOR REAL USERS | Demo UPI/card and Cash on Delivery are not a payment gateway |
| ₹0/month infrastructure | PASS FOR THIS WORK | Render Free plan retained; no paid resource, database, plan, or add-on was provisioned |
| Real-user pilot readiness | NOT READY | Persistence, identity, payment, backup/recovery, monitoring and performance gaps remain |

## Deployment boundary

Render's last observed deployed commit is `755e98922e16bc9b412b2eb653ed0517def14a42`, from PR #7. The
current GitHub baseline is `85c58bf5534dcd1390821250727b97e79d081c4b`; it contains runtime PostgreSQL
preparation from PR #10, but Render still uses the H2 `demo` profile. Deploying would restart the service.
Because no complete live H2 export and restore has been verified, deployment is **BLOCKED**. No duplicate
service or provider was created.

## Owner actions required before any deployment

1. Obtain a complete export of live H2 using an owner-controlled, supported method that does not restart the
   service; verify row counts, relationships, identifiers and restore feasibility in an isolated database.
2. Approve a specific zero-cost database provider only after reviewing its quota, pause, retention, backup and
   network limits. No provider is selected by this report.
3. Review and authorize the existing-service cutover only after a successful import/comparison and rollback plan.
4. Provide an authorized Admin test session only if live Admin acceptance is required. No account identifiers
   or credentials are included in this repository documentation.

Until these gates are satisfied, leave Render, its environment, H2 database and existing demo data unchanged.
