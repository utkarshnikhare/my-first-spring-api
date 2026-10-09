# SocioMart Copilot Release Handover — 2026-10-09

Current handover for the merged Recurring Offerings V2 and Seller Dashboard V3 release, including verification evidence, live-demo boundaries, and production-readiness work that remains deferred.

## Release state

- GitHub PR #7 merged normally to `main`.
- Merge commit: `755e98922e16bc9b412b2eb653ed0517def14a42`.
- Post-merge GitHub Actions CI passed.
- Existing Render service: `sociomart-demo`, connected to `main`.
- Render deployment `dep-db4e7uu0tbcc73e38o7g` reported **DEPLOYED** for the merge commit.
- Live URL: https://sociomart-demo.onrender.com
- Render auto-deploy remains disabled. Future deploys require an explicit manual action.

## Implemented release scope

The merged application contains Recurring Offerings V2 and Seller Dashboard V3. Recurring configuration is attached to an existing product; each selling date is a separate occurrence, with per-date overrides and occurrence-scoped order validation. The seller dashboard has LIVE and RECURRING views and contextual schedule actions. The buyer order service resolves and revalidates the selected occurrence. No new application code was needed during the post-merge deployment verification.

## Test evidence

- Complete Maven suite: **587 tests, 0 failures, 0 errors, 0 skipped**.
- Post-merge focused recurring/dashboard regression run: **66 tests, 0 failures, 0 errors, 0 skipped**.
- The focused run covered `RecurringFoundationPersistenceTest`, `RecurringScheduleCreateProductIntegrationTest`, `RecurringBuyerOrderFlowTest`, `RecurringScheduleHttpTest`, `SellerDashboardUiTest`, and `BuyerSellerUiUpdateTest`.
- GitHub Actions passed for the merge commit.
- `node --check` passed for all six tracked frontend JavaScript files.
- The previous 588-test figure in `requirements_matrix.md` was a reporting error; the verified total is 587.

## Live verification

Read-only checks after deployment confirmed:

- `/api/kitchens` and `/api/auth/config` return HTTP 200.
- Live `seller.js`, `buyer.js`, and `admin.js` match the merged Git files byte-for-byte.
- The seller app loads with LIVE as the default view; RECURRING opens successfully.
- The repeating-offering form displays one-time/repeating options, weekdays, durations, and the 90-day Ongoing cap. No offer was submitted.
- Buyer Food & Kitchens loads and returns discovery data.

Live transaction coverage is limited by the demo fixture. The seller currently has no recurring schedules, and public discovery contains no preorder/recurring items or next-occurrence dates. No live schedule or order was created because those writes would alter the public demo. Occurrence checkout and Poha override behavior are covered by the isolated local tests, not by a live public transaction.

The Admin route correctly denies the current Seller session; Admin workflows were not independently exercised with an Admin account. Homemade Products currently renders its empty state. Responsive checks found no reported overflow, but the browser's reported CSS width was scaled 1.5x, so exact 1440px/390px viewport certification remains open. The previous Cline report's 13/13 browser result is historical evidence, not a result independently reproduced in this handover.

## Data and deployment safety

No explicit database reset, environment-variable change, build-cache clear, duplicate service, live order, or live schedule was created during this work.

The demo profile uses in-memory H2 and reseeds on application startup. A Render redeploy restarts the application; consequently, non-seed runtime data from the old process cannot be guaranteed to persist across deployment. This is an existing demo configuration limitation, not a newly introduced persistent database migration.

## Security and performance boundaries

- Render is configured for the `demo` profile. Its mobile-number-only demo login and demo-mode CSRF behavior must not be treated as production authentication.
- The current security rules permit the OpenAPI/Swagger paths; verify and restrict them before any production launch.
- The Admin console rejected the browser's Seller session, but no separately authorized Admin account was used to test Admin workflows.
- No load test was run for the stated pilot targets (about 100 buyers, 50 sellers, 100 orders/day, and 100 active menu items). Query performance, image upload limits/storage/retention, and recovery operations remain unmeasured.
- Cross-browser Safari/WebKit and Firefox checks were not performed.

## Requirements source and coverage

The original V2/V3 DOCX files were unavailable in the workspace. Coverage was checked against the extracted texts in `REQ1_recurring_v2.txt` and `REQ2_dashboard_ui_v3.txt` and the repository tests. The matrix is intended to cover all requirements present in those extracts, but completeness against the unavailable binary specifications cannot be certified.

## Deferred production-readiness checklist

These are not demo-release blockers and were not implemented:

1. Select and provision a durable database; define migration ownership and schema compatibility.
2. Plan and test backup/restore and rollback before moving any data.
3. Replace demo mobile-number login with an approved production identity flow before handling real user data.
4. Restrict or disable public OpenAPI/Swagger exposure for production.
5. Define production logging, monitoring, upload storage/retention, and recovery requirements.
6. Benchmark the intended pilot workload and review database query/pagination behavior.
7. Add concurrency and duplicate-submission tests where pilot workload requires them.

Do not repeat the V2/V3 implementation. For future code changes, branch from current `main`, preserve unrelated untracked files, run the full Maven suite, and use a normal PR/CI/merge path. Documentation-only changes do not require redeploying the app; Render auto-deploy is disabled.
