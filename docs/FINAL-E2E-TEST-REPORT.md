# SocioMart Final E2E Test Report

## Current local feature-branch verification — 2026-10-10

The branch `copilot/demo-registration-e2e` is not yet merged or deployed. Local browser tests use the
Playwright-managed demo-profile application and an in-memory H2 database. No live order, schedule, delivery,
payment, Render environment, or live database write was submitted.

| Tier | Command / readiness | Result |
|---|---|---|
| Startup | Playwright started `mvnw.cmd -DskipTests -Dspring-boot.run.profiles=demo spring-boot:run`; `/api/auth/config` became ready at `http://127.0.0.1:18082` | PASS |
| Maven | `.\mvnw.cmd -B test` from `my-first-spring-api`; exit code 0 | 592 tests; 0 failures; 0 errors; 1 skipped |
| Browser E2E | `npx playwright test`; exit code 0 | 4 passed, 0 failed; desktop and Pixel 7 projects |

**Browser journeys exercised**

| Journey | Browser evidence |
|---|---|
| Buyer registration and login | OTP/SMS not requested; required profile submitted; duplicate registration refused; wrong password rejected; valid password login and persisted profile verified |
| Seller registration and approval | Required seller/store/contact/location/service-coverage fields submitted; `PENDING` status persisted; pending Seller APIs denied; invalid credentials rejected; Admin approval succeeds; approved Seller gains Seller access but not Admin access |
| Buyer-to-Seller order lifecycle | Seller creates an offering; Buyer discovers it, selects it, checks out with a remark, and submits once despite a repeated click; stock reaches sold out; Seller marks delivery complete; Buyer history shows the final delivered status and remark |
| Mobile registration | Buyer and Seller registration/login tested at the mobile viewport; no horizontal overflow; pending Seller state remained visible |

Browser diagnostics failed on unexpected page errors, failed API requests and unexpected API responses. One
earlier lifecycle attempt observed HTTP 409 from `GET /api/notifications`; the response body is now captured
if that occurs again. The targeted lifecycle rerun and the complete four-test browser suite both passed, so the
409 was not reproduced and no root cause is claimed.

**Environment and gaps:** Node.js `v22.23.2` and Playwright `1.64.0` were available. Browser binaries were
usable by the passing suite. Docker was unavailable because the executable was not installed; the
PostgreSQL/Testcontainers test was skipped. Local H2 results do not certify PostgreSQL compatibility.

Overall local validation is **PASS for the executed tests, PARTIAL for release acceptance**: recurring
occurrence checkout/overrides, cancellation, simultaneous final-unit purchases, complete Admin workflows,
and the full flow against live Render were not exercised by this browser suite. See the
[requirements audit](./FINAL-REQUIREMENTS-AUDIT.md) for source-level status.

## Automated application-flow evidence

Earlier full-build evidence: `.\mvnw.cmd -B clean verify` from `my-first-spring-api`.

| Scenario | Evidence | Result |
|---|---|---|
| Kitchen setup, explicit service coverage, offering creation/discovery, multi-item buyer order, persisted order and stock decrement | `DemoMarketplaceE2ETest` | Passed in the full suite |
| Invalid quantity, stale stock and authorization boundaries | `DemoMarketplaceE2ETest`, `OrderServiceValidationTest` and security integration tests | Passed in the full suite |
| Recurring schedule creation, date-specific overrides, history retention and occurrence-separated orders | `RecurringScheduleCreateProductIntegrationTest`, `RecurringScheduleHttpTest`, `RecurringFoundationPersistenceTest`, `RecurringBuyerOrderFlowTest` | Passed in the full suite |
| Delivery status, occurrence scope, summaries and buyer-visible state | `SellerDeliveryCompletionServiceTest`, `SellerDeliveryProgressSummaryTest`, `SellerOrderDetailUiTest` | Passed in the full suite |
| Cancellation stock restoration | `InventoryRestorationIntegrationTest` | Passed in the full suite |
| Admin filters, recorded value, location management, audit, exports, role scope and retention safeguards | `AdminV1ScopeTest`, `AdminV2HandoverTest`, `AdminHandoverGapTest`, `AdminLocationsManagementTest` | Passed in the full suite |
| H2 PostgreSQL-mode migration and JPA validation | Maven verification | Passed locally |
| Full backend test suite (current branch) | `.\mvnw.cmd -B test` | **592 tests, 0 failures, 0 errors, 1 skipped; BUILD SUCCESS** |

The skipped test is the native PostgreSQL Testcontainers test because Docker is unavailable locally. Backend
unit/integration results do not prove every browser flow or simulate simultaneous live buyers.

## Historical read-only live browser/API checks — 2026-10-09

These observations predate the current unmerged branch and describe the then-deployed code only.

| Page / flow | Observation | Status |
|---|---|---|
| Buyer `/` | Buyer landing page loaded | LIVE SMOKE |
| Seller `/seller.html` | Seller entry/dashboard loaded | LIVE SMOKE |
| Admin `/admin.html` | Admin sign-in route loaded; no authorized session used | ROUTE SMOKE ONLY |
| `GET /api/kitchens` | HTTP 200; 16 seeded kitchens returned | LIVE PASS |
| `GET /api/auth/config` | HTTP 200; demo login enabled | LIVE PASS |
| Seller LIVE/RECURRING tabs | Both rendered; RECURRING showed an empty state | LIVE SMOKE |
| Buyer preorder comparison | Search for `Puran Poli (Pre-order)` rendered one Aarti Kitchen offer, dated 2026-10-12, at ₹70/piece | LIVE PASS |
| Buyer discovery API | Exact preorder query returned HTTP 200 with one `PRE_ORDER` result for kitchen ID 1 | LIVE PASS |
| Buyer occurrence-specific checkout | No order was submitted | NOT TESTED |
| Seller order processing / delivery | No public order or delivery state was changed | NOT TESTED |
| Admin operational actions | No authorized Admin session was used | NOT TESTED |
| Live payment | No real or simulated public payment was submitted | NOT TESTED |

The first preorder page snapshot showed its loading state. After the response completed, the comparison card
and matching API result appeared; this corrected the earlier premature “no offer” observation. The card's
external example image request was blocked by the browser, but the text and offer details rendered.

## Responsive and browser limits

Earlier local headless-browser measurements covered Buyer, Seller and Admin document widths at the listed
desktop/mobile targets; the 390px request measured 391 CSS pixels, so exact 390px certification remains
inconclusive. These measurements do not prove keyboard accessibility, all control touch targets, all images,
or Safari/Firefox behavior.

## Not tested

- A complete live order/stock/seller-fulfilment/buyer-history journey; public business writes were avoided.
- Simultaneous buyers competing for the final unit; code uses database locking, but no concurrent acceptance
  test was found in the reviewed evidence.
- Live Admin workflows with authorized credentials.
- Production authentication, persistent-database migration, payment gateway, load/performance, or restore.
- Original specification DOCX binaries; the seven extracted user-provided text sources were reviewed from
  session scratch and are not committed as project data.
