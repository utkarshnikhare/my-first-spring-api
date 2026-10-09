# SocioMart Final E2E Test Report

Date: 2026-10-09. All live requests below were read-only. No order, schedule, payment, database, environment
variable, or Render deployment was created or changed.

## Automated application-flow evidence

Command: `.\mvnw.cmd -B clean verify` from `my-first-spring-api`.

| Scenario | Evidence | Result |
|---|---|---|
| Create new kitchen, explicit society coverage, create offerings, discover kitchen, place a multi-item order, persist order, deduct stock, and expose order to Buyer/Seller | `DemoMarketplaceE2ETest` | 3 tests passed in the final full suite |
| Invalid/valid quantities and stock correctness | `DemoMarketplaceE2ETest.inventoryQuantityCorrectness` | Passed |
| Authorization boundaries | `DemoMarketplaceE2ETest.authorizationBoundaries` and security integration tests | Passed |
| Recurring occurrence selection and date-separated orders | `RecurringBuyerOrderFlowTest` | Passed |
| Recurring creation and HTTP schedule actions | `RecurringScheduleCreateProductIntegrationTest`, `RecurringScheduleHttpTest` | Passed |
| Per-date quantity/time override and history retention | `RecurringFoundationPersistenceTest` | Passed, including the Wednesday/Friday acceptance scenario |
| Delivery update, occurrence scope and buyer-visible state | recurring/order/delivery service tests | Passed at service/controller integration level |
| PostgreSQL schema migration | H2 PostgreSQL-mode migration test | Passed locally; native PostgreSQL Testcontainers case skipped locally because Docker is unavailable |

The backend tests use isolated test databases and test fixtures. They are not a real browser payment submission.

## Read-only live browser and API checks

| Page / flow | Observation | Status |
|---|---|---|
| Buyer `/` | Buyer home loaded and rendered location, Food & Kitchens, Homemade Products and navigation | LIVE SMOKE |
| Buyer `/#/food` | Food & Kitchens loaded categories/items. No Pre-order section/item appeared in the observed data | LIVE SMOKE; no preorder sample |
| Seller `/seller.html` | Seller dashboard loaded with LIVE and RECURRING tabs, summary cards and earnings section | LIVE SMOKE |
| Seller RECURRING tab | Tab rendered schedule-management copy and an empty state; no schedule was created | LIVE SMOKE |
| Admin `/admin.html` | Admin sign-in form rendered | Route smoke only; authorized Admin workflows NOT TESTED |
| `GET /api/kitchens` | HTTP 200 (`application/json`) | PASS |
| Buyer occurrence checkout | No suitable live recurring/preorder item was available; no order was submitted | NOT TESTED |
| Live payment | No real or simulated public payment transaction was submitted | NOT TESTED |
| Live database preservation | No database snapshot/import/restart performed | BLOCKED |

The browser recorded anonymous Buyer `401` responses for protected profile requests and a failed request for
an external example image URL on the Seller page. The Buyer home still rendered; this report does not infer
that protected-profile responses are a server defect or that every configured image URL is valid.

## Responsive measurements

Measured using Playwright `page.setViewportSize` and page DOM metrics for Buyer, Seller and Admin pages.
Integrated browser `devicePixelRatio` was approximately `0.75`, so requested physical sizes were scaled to
produce the specified CSS viewport. At the 390x844 target, the browser reported 391x844 CSS px; exact 390px
certification is therefore inconclusive. No horizontal overflow was observed at the measured CSS widths.

| Requested CSS viewport | Buyer | Seller | Admin |
|---|---|---|---|
| 360x800 | exact; no horizontal overflow | exact; no horizontal overflow | exact; no horizontal overflow |
| 375x812 | exact; no horizontal overflow | exact; no horizontal overflow | exact; no horizontal overflow |
| 390x844 | measured 391x844; no overflow | measured 391x844; no overflow | measured 391x844; no overflow |
| 412x915 | exact; no horizontal overflow | exact; no horizontal overflow | exact; no horizontal overflow |
| 768x1024 | exact; no horizontal overflow | exact; no horizontal overflow | exact; no horizontal overflow |
| 1024x768 | exact; no horizontal overflow | exact; no horizontal overflow | exact; no horizontal overflow |
| 1440x900 | exact; no horizontal overflow | exact; no horizontal overflow | exact; no horizontal overflow |
| 1920x1080 | exact; no horizontal overflow | exact; no horizontal overflow | exact; no horizontal overflow |

These measurements establish document width only. They do not certify every control's touch target, keyboard
accessibility, clipping, overlap, image quality, or cross-browser behavior. Persistent screenshot files were
not saved in this run.

## Not tested or blocked

- No complete browser-based local Buyer/Seller/Admin recurring-order journey was replayed in this run.
- No live Seller schedule or Buyer order was created because that would alter the public demo.
- No authorized Admin session was used.
- No Safari/WebKit or Firefox run, load test, performance benchmark, or image-storage test was run.
- Original DOCX specifications are absent; only the repository's extracted text was available.

See [the acceptance matrix](./FINAL-V2-V3-ACCEPTANCE-MATRIX.md) and
[the regression report](./FINAL-REGRESSION-REPORT.md) for additional evidence.
