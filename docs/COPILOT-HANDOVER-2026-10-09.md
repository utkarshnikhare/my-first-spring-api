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

- Original V2/V3 release suite: **587 tests, 0 failures, 0 errors, 0 skipped**.
- Current persistence-preparation branch: `mvn -B verify` completed with **589 tests, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS**. The new H2 PostgreSQL-mode migration test passed; the native PostgreSQL Testcontainers case was skipped locally because Docker is unavailable.
- Post-merge focused recurring/dashboard regression run: **66 tests, 0 failures, 0 errors, 0 skipped**.
- The focused run covered `RecurringFoundationPersistenceTest`, `RecurringScheduleCreateProductIntegrationTest`, `RecurringBuyerOrderFlowTest`, `RecurringScheduleHttpTest`, `SellerDashboardUiTest`, and `BuyerSellerUiUpdateTest`.
- One time-sensitive recurring buyer assertion was made stable by keeping its test occurrence open until 23:59; the application behavior was not changed.
- GitHub Actions passed for the merge commit.
- `node --check` passed for all six tracked frontend JavaScript files.
- The old matrix's 588 figure was a reporting error for the original release; the current 589 figure includes two persistence-migration test cases, with the native PostgreSQL case skipped locally because Docker is unavailable.

## Zero-budget database preparation

The current branch `copilot/persistent-database-migration` prepares a `postgres-demo` profile with Flyway V1, PostgreSQL/JDBC dependencies, and Hibernate schema validation. The existing H2 demo remains the default. `postgres-demo` does not run demo seeders, so an imported dataset is not mixed with fresh sample rows. Demo login remains enabled only for that explicitly selected demo profile.

The local migration passed against H2 PostgreSQL mode and Hibernate validated all mapped tables. The actual PostgreSQL Testcontainers test is committed to the branch but could not run on this machine because Docker is unavailable; GitHub Actions is expected to run it on its Docker-enabled runner. No free or paid provider has been provisioned and no Render environment variables or deploy settings changed.

Current branch: `copilot/persistent-database-migration`, based on main commit `484171d72dc81ace7b965090a4698894e02f0903`. It is preparation only and still requires a normal PR/CI review before merge. Render remains on the already deployed H2 demo release.

### Preservation blocker

The deployed service still has in-memory H2. A read-only visit to its H2 Console returned “remote connections are disabled,” and the application exposes no complete database dump endpoint. I did not bypass that setting, restart the service, or replace the database. A full export must be obtained through an owner-controlled live-process path or supplied by the service owner before any cutover; standard REST listing/export endpoints do not prove that all entity data, IDs, relationships, and sequence state were preserved.

### Zero-cost provider comparison

- **Neon Free** is the first candidate to evaluate: official limits list 1 GB/project, 100 CU-hours/project/month, 5 GB public transfer, and compute scale-to-zero after five minutes. Compute/transfer exhaustion suspends connections until quota resets; stored data remains. Cold starts, public-network dependence, quotas, and lack of paid-tier guarantees remain.
- **Supabase Free** offers 500 MB database storage. Low activity can pause a project after seven days; paused projects can be restored for up to a year, but Free has no automatic daily backups/PITR. It requires manual exports and resume operations.
- **Render Free Postgres** has 1 GB but expires after 30 days, with a 14-day upgrade grace period and no automated backups; it is not suitable for the requested preserved dataset.

No provider is selected or provisioned. Before switching the existing Render service, require a safe full export, verified native PostgreSQL CI, an owner-approved free provider, and a tested count/relationship comparison.

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

These are not V2/V3 demo release blockers:

1. Obtain a full, safe export of current in-memory H2 data without restarting the service.
2. Select a free PostgreSQL provider after reviewing quotas, pause/retention behavior, backups, latency, and uptime limitations.
3. Run the PostgreSQL Testcontainers test in CI and compare preserved data after import before any cutover.
4. Replace demo mobile-number login with an approved production identity flow before handling real customer data.
5. Restrict or disable public OpenAPI/Swagger exposure for production.
6. Define production logging, monitoring, upload storage/retention, and recovery requirements.
7. Benchmark the intended pilot workload and review database query/pagination behavior.
8. Add concurrency and duplicate-submission tests where pilot workload requires them.

Do not repeat the V2/V3 implementation. For future code changes, branch from current `main`, preserve unrelated untracked files, run the full Maven suite, and use a normal PR/CI/merge path. Documentation-only changes do not require redeploying the app; Render auto-deploy is disabled.
