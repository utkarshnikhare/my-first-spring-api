# SocioMart Final E2E Test Report

Date: 2026-10-09. The local automated suite uses isolated test data. Live checks below were read-only; no
order, schedule, delivery, payment, environment, or database write was submitted.

## Automated application-flow evidence

Command: `.\mvnw.cmd -B clean verify` from `my-first-spring-api`.

| Scenario | Evidence | Result |
|---|---|---|
| Kitchen setup, explicit service coverage, offering creation/discovery, multi-item buyer order, persisted order and stock decrement | `DemoMarketplaceE2ETest` | Passed in the full suite |
| Invalid quantity, stale stock and authorization boundaries | `DemoMarketplaceE2ETest`, `OrderServiceValidationTest` and security integration tests | Passed in the full suite |
| Recurring schedule creation, date-specific overrides, history retention and occurrence-separated orders | `RecurringScheduleCreateProductIntegrationTest`, `RecurringScheduleHttpTest`, `RecurringFoundationPersistenceTest`, `RecurringBuyerOrderFlowTest` | Passed in the full suite |
| Delivery status, occurrence scope, summaries and buyer-visible state | `SellerDeliveryCompletionServiceTest`, `SellerDeliveryProgressSummaryTest`, `SellerOrderDetailUiTest` | Passed in the full suite |
| Cancellation stock restoration | `InventoryRestorationIntegrationTest` | Passed in the full suite |
| Admin filters, recorded value, location management, audit, exports, role scope and retention safeguards | `AdminV1ScopeTest`, `AdminV2HandoverTest`, `AdminHandoverGapTest`, `AdminLocationsManagementTest` | Passed in the full suite |
| H2 PostgreSQL-mode migration and JPA validation | Maven verification | Passed locally |
| Full backend and packaged build | Maven verification | **589 tests, 0 failures, 0 errors, 1 skipped; BUILD SUCCESS** |

The skipped test is the native PostgreSQL Testcontainers test because Docker is unavailable locally. This
suite does not prove every browser flow or simulate simultaneous live buyers; those remain separately tracked
in the [requirements audit](./FINAL-REQUIREMENTS-AUDIT.md).

## Read-only live browser/API checks

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
