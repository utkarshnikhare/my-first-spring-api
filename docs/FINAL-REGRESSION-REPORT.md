# SocioMart Final Regression Report

Verification snapshot: 2026-10-09. The worktree was based on `origin/main` at
`85c58bf5534dcd1390821250727b97e79d081c4b`. The only application-source change in this PR is a
test-fixture stabilization in `DemoMarketplaceE2ETest`; runtime application behavior is unchanged.

## Automated checks

| Check | Command / environment | Result |
|---|---|---|
| Full backend and packaged build | `.\mvnw.cmd -B clean verify`, JDK 21-compatible Maven build, local isolated H2 | **589 tests; 0 failures; 0 errors; 1 skipped; BUILD SUCCESS** |
| Targeted end-to-end fixture regression | `.\mvnw.cmd -B -Dtest=DemoMarketplaceE2ETest test` | **3 tests; 0 failures; 0 errors; 0 skipped; BUILD SUCCESS** |
| Frontend syntax | `node --check` for `admin.js`, `app.js`, `buyer.js`, `common.js`, `config.js`, `seller.js` | **6/6 passed** |
| H2/PostgreSQL-mode Flyway + JPA validation | Included in Maven verification | Passed |
| Native PostgreSQL Testcontainers | Local machine without Docker | Skipped locally; prior Docker-enabled GitHub Actions run passed the native migration test |
| GitHub Actions PR check | [Run 37962316067](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37962316067) on PR #13 head `b1dc83a` | Success |
| GitHub Actions merged-main check | [Run 37962564417](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37962564417) at `58932f5` | Success |

The initial full-suite attempt at 22:02 had three errors in `DemoMarketplaceE2ETest`: its helper capped the
same-day cutoff at 22:00 and moved a late run to a future pre-order date, which was blocked by the test
kitchen's disabled preorder entitlement. The test helper now keeps its fixture a same-day offering with a
23:59 close and next-day ready time. The targeted class and full suite passed afterward. Production code and
business rules were not changed.

## Coverage areas

- Buyer/Seller/Admin service and controller regressions, role boundaries and ownership checks.
- Inventory, duplicate submission, order totals, payment/delivery status, cancellation and delivery completion.
- Recurring schedule creation, per-occurrence overrides, future schedule edits, history and occurrence-scoped orders.
- H2 PostgreSQL-mode schema migration and Hibernate validation.
- UI/script structure checks for dashboard tabs, delivery filters, occurrence actions and responsive CSS rules.

Detailed named test evidence is in `requirements_matrix.md` and
[the V2/V3 acceptance matrix](./FINAL-V2-V3-ACCEPTANCE-MATRIX.md).

## Live browser / performance / security limits

Read-only public route/API smoke and responsive document-width measurements are in
[the E2E report](./FINAL-E2E-TEST-REPORT.md). The live occurrence checkout, Admin workflows, real payments,
concurrency under load, pilot-scale performance, external image storage and restore operations were not tested.
The suite's security tests are automated role/ownership checks; this is not a formal penetration test.
Swagger/OpenAPI is currently enabled for the demo and must be restricted before production use.

The full suite ran against the release branch after the test-fixture fix. PR #13 passed GitHub CI and merged;
the resulting `main` push workflow also passed.
