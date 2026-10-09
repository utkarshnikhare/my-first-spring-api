# Final GitHub and Render Status

Audit snapshot: 2026-10-09. The demo deployment was explicitly authorized after the owner confirmed that
non-seed H2 records were disposable. No paid resource, PostgreSQL database, duplicate Render service, real
payment, or production authentication migration was introduced.

## GitHub

| Item | Status | Evidence |
|---|---|---|
| Latest GitHub `main` commit | `39116ce55273cc0ae5174173a3f89254a4bf2039` | PR #15 merge commit; documentation-only |
| Latest application/runtime commit on `main` | `d9bb8782ff58719a68df01236c09e9e0990c5bf4` | Runtime source is unchanged by PR #15 and matches the deployed application |
| Latest `main` CI | PASS | [Actions run 37972517277](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37972517277) succeeded on the merge commit |
| PR #15 CI | PASS | [Actions run 37972344315](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37972344315); PR merged normally |
| Release PRs | #7 through #15 merged | Normal GitHub PR workflow; no force-push or protection bypass |
| README and audit documentation | PASS | Updated in PR #15 and merged to `main` |
| GitHub About description | PASS | Verified with `gh repo view`; describes Spring Boot + vanilla JavaScript and demo scope |
| Repository homepage | PASS | `https://sociomart-demo.onrender.com/`, verified with `gh repo view` |
| Repository topics | PASS | Existing topics retained; `flyway`, `postgresql`, `recurring-offerings`, and `seller-dashboard` present |
| Public Quick Links | PASS | Buyer, Seller, Admin, repository, and requirements-matrix links return/load as expected |
| API documentation Quick Link | Intentionally omitted | Swagger/OpenAPI remains unsuitable as a public production link until hardened |

PR #15 merged the documentation refresh as commit `39116ce55273cc0ae5174173a3f89254a4bf2039`.
It changed documentation only; no Render redeployment was required. GitHub `main` now has that documentation
commit while the runtime application remains at `d9bb8782ff58719a68df01236c09e9e0990c5bf4`.

## Render

| Item | Status | Evidence |
|---|---|---|
| Existing service | PASS | `sociomart-demo`, service `srv-dad5lfajnfac73ei06s0`; no duplicate created |
| Plan / budget | PASS | Render dashboard showed **Free**; no paid plan/add-on or external database provisioned |
| Deployed commit | PASS | `d9bb8782ff58719a68df01236c09e9e0990c5bf4` |
| Deployment | PASS | `dep-db4i1rqj9qps73alfiug` shown as the last successful deployment |
| GitHub/Render runtime synchronization | YES (runtime) | The latest `main` change is documentation-only; runtime source on `main` and Render remain at `d9bb8782ff58719a68df01236c09e9e0990c5bf4` |
| Health/config endpoints | PASS | `GET /api/kitchens` and `GET /api/auth/config` returned HTTP 200 |
| Seed data after restart | PASS | `/api/kitchens` returned 16 kitchens after deployment; demo login remained enabled |
| Buyer pre-order discovery | PASS (read-only) | Search route and `/api/discovery/offers?item=Puran%20Poli%20%28Pre-order%29` returned one Aarti Kitchen preorder for 2026-10-12 |
| Seller LIVE/RECURRING tabs | LIVE SMOKE | LIVE displayed seeded offerings; RECURRING opened with an empty state |
| Buyer occurrence checkout | NOT TESTED | No public order was submitted |
| Admin operations | NOT TESTED | Admin sign-in route loaded; no authorized Admin workflow was exercised |
| Application logs | PASS with non-fatal warnings | Startup completed on H2 demo profile; no startup/Flyway/database/authentication exception was observed in the checked logs |
| Data preservation | PASS within authorized demo scope | Disposable non-seed in-memory H2 records could reset; seeded records were present afterward |

The first Buyer comparison snapshot was still loading. After the live response completed, the page rendered
the preorder card and the matching read-only API query returned one offer. This was a cold-start/loading delay,
not a confirmed API/UI data mismatch. The example kitchen image URL was blocked by the browser; it did not
prevent the offer card from rendering.

## Remaining acceptance limits

- No live order, cancellation, delivery update, schedule edit, or payment transaction was submitted.
- No authorized Admin session was used for live operational testing.
- Seller registration with OTP, a persisted pending-application flow, and stable reserved public kitchen slugs
  are not part of the current demo authentication/routing implementation. They remain blocked from this release
  by the explicit instruction not to undertake an authentication migration.
- The demo remains H2/in-memory and is not production-persistent. A future storage migration needs a separate
  owner-approved design and must preserve the ₹0/month ceiling.
- The complete source-requirement reconciliation and evidence limits are in
  [the final requirements audit](./FINAL-REQUIREMENTS-AUDIT.md).
