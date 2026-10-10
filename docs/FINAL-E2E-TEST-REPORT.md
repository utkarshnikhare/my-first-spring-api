# SocioMart Final E2E Test Report

## Public health and inventory follow-up

The current change adds a public `GET /api/health` response (`{"status":"UP"}`), permits it without
authentication, and configures it as the Render health-check path. A security integration test verifies the
public health response and confirms that an Admin API remains protected.

Checkout tests now verify persisted order creation and stock decrement; cancellation tests verify persisted
status and one-time inventory restoration; a synchronized two-buyer test verifies that exactly one buyer
obtains the final unit and inventory never drops below zero. Authentication integration-test password values
are generated at runtime rather than stored as literals.

| Verification | Result |
|---|---|
| Live `GET /api/kitchens` | HTTP 200 |
| Live `GET /api/auth/config` | HTTP 200 |
| Live `GET /api/health` | HTTP 401; the current Render runtime does not yet contain this change |
| Focused `DirectRegistrationSecurityIntegrationTest` | 3 passed; 0 failures/errors |
| `.\mvnw.cmd -B clean verify` | BUILD SUCCESS; 597 tests, 0 failures, 0 errors, 1 skipped across 78 test classes |
| PostgreSQL/Testcontainers | 1 test skipped because Docker is unavailable |

The owner confirmed that the demo Admin and Super Admin passwords were rotated and the existing service was
redeployed. No password was requested or used during this follow-up. The new health endpoint has passed its
local security test but is not yet available on the live service; do not treat the current live 401 as a
successful health check. No live order, cancellation, or concurrent-purchase test was performed.

## Latest release verification — 2026-10-10

PR #18 is merged to GitHub `main` at `0b3f1bc66f41c61e49a74138f8749bd32a88638e`; CI run
[38029688055](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/38029688055) passed.
The existing Render Free service deployed that exact commit. See the
[GitHub and Render status](./FINAL-GITHUB-RENDER-STATUS.md) for deployment identity and health-endpoint limits.

In addition to the local tests below, a live disposable Buyer-to-Seller lifecycle passed through final
delivery: direct Buyer registration/login without OTP, direct Seller registration with PENDING approval,
Admin approval, Seller offering creation, Buyer discovery/checkout, single order creation, inventory reaching
zero/sold-out, Seller receipt, delivery completion, and Buyer history showing `DELIVERED` with the original
remark. Final Seller and Buyer reads agreed. The order remained `ORDERED` and payment `PENDING`; no payment
was attempted. This is one live scenario, not evidence for recurring occurrence checkout, cancellation,
concurrent buyers, or exhaustive Buyer/Seller/Admin coverage.

## Local verification of merged implementation — 2026-10-10

Local tests use the demo profile and in-memory H2. Docker is unavailable locally, so PostgreSQL/Testcontainers
coverage is skipped.

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

Overall local validation is **PASS for the executed tests, PARTIAL for the full requirements set**: recurring
occurrence checkout/overrides, cancellation, simultaneous final-unit purchases, and complete Admin workflows
were not exercised by the local browser suite. The single live lifecycle above is separately verified; it does
not close those remaining coverage gaps. See the
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
