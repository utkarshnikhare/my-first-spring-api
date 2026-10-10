# Final GitHub and Render Status

## Latest release and live verification — 2026-10-10

- PR #18 was merged normally. GitHub `main` is
  `0b3f1bc66f41c61e49a74138f8749bd32a88638e`; its CI run
  [38029688055](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/38029688055)
  succeeded.
- The existing Render service `sociomart-demo` (`srv-dad5lfajnfac73ei06s0`) remains on the **Free** plan.
  Deployment `dep-db4un6942hec73f2rfbg` succeeded and reports the exact same source commit as GitHub `main`.
  No duplicate service, paid resource, PostgreSQL database, or real payment was used.
- Live Buyer, Seller, and Admin routes (`/`, `/seller.html`, `/admin.html`) returned HTTP 200. The public
  auth configuration and kitchens endpoints returned HTTP 200; seeded listings, including the preorder,
  were available. Seller LIVE and RECURRING tabs opened. Admin login/operations and role boundaries were
  exercised; unauthorized Buyer/Seller access to Admin and Admin access to Super Admin were denied.
- OTP-free Buyer registration/login and Seller registration were exercised. Duplicate Buyer registration was
  rejected; the Seller remained PENDING until Admin approval. The approved Seller created a one-unit offering;
  Buyer checkout created one order, inventory reached zero/sold-out, and Seller received the order and remark.
- Seller API and Buyer order history both subsequently reported `deliveryStatus: DELIVERED`; Buyer history
  displayed the delivery badge and preserved the remark. The separate `orderStatus` remained `ORDERED` and
  payment remained `PENDING`; no payment was attempted. A preceding Seller read had still returned
  `NOT_DELIVERED`, so only the later matching Seller and Buyer reads are treated as final verification.
- `GET /actuator/health` returned HTTP 401. Therefore a successful public health-endpoint response is **not
  verified**; HTTP 200 route/API checks and Render's successful deployment status are not a substitute for it.
- The demo Admin and Super Admin password variables are configured in Render and were used for authorized
  checks. Their values were exposed in prior tool output and must be rotated by the owner immediately. Open
  the existing service's **Environment** page and replace both
  `SOCIOMART_DEMO_ADMIN_PASSWORD` and `SOCIOMART_DEMO_SUPER_ADMIN_PASSWORD` with new private values.
  Do not reuse the exposed values or share the replacements in chat, GitHub, or documentation. Saving may
  restart the Free service and reset its disposable in-memory H2 records.
- The full live order path above is verified for one disposable test order only. Recurring occurrence checkout,
  cancellation, simultaneous final-unit buyers, and comprehensive Admin workflows remain unverified live.
  Local test results and their limits are in the [E2E test report](./FINAL-E2E-TEST-REPORT.md).

## Historical snapshot — 2026-10-09

The following record was captured after PR #16 and before the current feature branch. At that
snapshot, GitHub `main` is `b70d13239776fc9221dfa6dfdca0de1910afd982`. The demo deployment was explicitly authorized after the owner confirmed that
non-seed H2 records were disposable. No paid resource, PostgreSQL database, duplicate Render service, real
payment, or production authentication migration was introduced.

## GitHub

| Item | Status | Evidence |
|---|---|---|
| Latest GitHub `main` commit at this snapshot | `b70d13239776fc9221dfa6dfdca0de1910afd982` | PR #16 merge commit; documentation-only |
| Latest application/runtime commit on `main` | `d9bb8782ff58719a68df01236c09e9e0990c5bf4` | Runtime source is unchanged by PRs #15 and #16 and matches the deployed application |
| Latest `main` CI at this snapshot | PASS | [Actions run 37973393842](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37973393842) succeeded on the PR #16 merge commit |
| PR #16 CI | PASS | [Actions run 37973218957](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37973218957); PR merged normally |
| Release PRs | #7 through #16 merged | Normal GitHub PR workflow; no force-push or protection bypass |
| README and audit documentation | PASS | Updated in documentation-only PRs #15 and #16 and merged to `main` |
| GitHub About description | PASS | Verified with `gh repo view`; describes Spring Boot + vanilla JavaScript and demo scope |
| Repository homepage | PASS | `https://sociomart-demo.onrender.com/`, verified with `gh repo view` |
| Repository topics | PASS | Existing topics retained; `flyway`, `postgresql`, `recurring-offerings`, and `seller-dashboard` present |
| Public Quick Links | PASS | Buyer, Seller, Admin, repository, and requirements-matrix links return/load as expected |
| API documentation Quick Link | Intentionally omitted | Swagger/OpenAPI remains unsuitable as a public production link until hardened |

PR #15 merged the requirements audit as `39116ce55273cc0ae5174173a3f89254a4bf2039`; PR #16 recorded
post-merge evidence as `b70d13239776fc9221dfa6dfdca0de1910afd982`. Both changed documentation only; no Render
redeployment was required. At this report snapshot, GitHub `main` is at the PR #16 merge while runtime source
remains at `d9bb8782ff58719a68df01236c09e9e0990c5bf4`.

## Render

| Item | Status | Evidence |
|---|---|---|
| Existing service | PASS | `sociomart-demo`, service `srv-dad5lfajnfac73ei06s0`; no duplicate created |
| Plan / budget | PASS | Render dashboard showed **Free**; no paid plan/add-on or external database provisioned |
| Deployed commit | PASS | `d9bb8782ff58719a68df01236c09e9e0990c5bf4` |
| Deployment | PASS | `dep-db4i1rqj9qps73alfiug` shown as the last successful deployment |
| GitHub/Render runtime synchronization | YES (runtime) | The latest `main` changes are documentation-only; runtime source on `main` and Render remain at `d9bb8782ff58719a68df01236c09e9e0990c5bf4` |
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
- The current feature branch adds OTP-free direct Buyer/Seller registration and pending Seller approval, but
  that behavior is not live until it is merged and deployed.
- Live Admin/Super Admin sign-in is blocked until the owner configures the two role-specific Render environment
  variables listed above.
- The demo remains H2/in-memory and is not production-persistent. A future storage migration needs a separate
  owner-approved design and must preserve the ₹0/month ceiling.
- The complete source-requirement reconciliation and evidence limits are in
  [the final requirements audit](./FINAL-REQUIREMENTS-AUDIT.md).
