# SocioMart V2/V3 Final Acceptance Matrix

Verification snapshot: 2026-10-09. Scope is the extracted V2/V3 specifications and current automated
coverage. The original DOCX files are not present in the workspace; requirements not represented in the
extracted text cannot be certified.

Status terms: **PASS (automated)** means current implementation is covered by tests/source-contract checks;
it does not imply live Render transaction acceptance. **LIVE SMOKE** is read-only browser/API evidence.
**NOT TESTED** is deliberately not treated as PASS.

## Recurring Offerings V2

| ID | Source section | Expected behavior | Implementation | Test/evidence | Status | Defect |
|---|---|---|---|---|---|---|
| V2-01 | 3 | Reuse Create Offering for one-time and repeating availability | `seller.js`, `ProductCreateDto`, `SellerService` | `RecurringScheduleCreateProductIntegrationTest`; `BuyerSellerUiUpdateTest` | PASS (automated) | — |
| V2-02 | 3.1 | Preserve one-time Today/Tomorrow/Choose Date behavior | `seller.js`, `OfferingTiming` | `BuyerSellerUiUpdateTest`; `OfferingTimingTest` | PASS (automated) | — |
| V2-03 | 3.2 | Support start date, weekday selection and duration choices | `seller.js`, `RecurringScheduleService` | `RecurringScheduleHttpTest`; `RecurringScheduleCreateProductIntegrationTest` | PASS (automated) | — |
| V2-04 | 3.2, 15 | Cap Ongoing at 90 days and permit safe extension | `RecurringScheduleService`, `RecurringScheduleController` | `RecurringScheduleHttpTest`; `RecurringFoundationPersistenceTest` | PASS (automated) | — |
| V2-05 | 3.2, 4 | Store schedule defaults, occurrences and overrides separately | `RecurringSchedule`, `Occurrence`, `OccurrenceOverride` | `RecurringFoundationPersistenceTest`; schema migration tests | PASS (automated) | — |
| V2-06 | 5 | Reset quantity and time defaults for each date | `RecurringScheduleService`, occurrence materialization | `RecurringFoundationPersistenceTest` | PASS (automated) | — |
| V2-07 | 6 | Apply the Wednesday quantity/time change only to Wednesday | occurrence override resolution | `RecurringFoundationPersistenceTest.pohaAcceptanceOverrideFutureDefaultsPastHistoryAndEndAreIsolated` | PASS (automated) | — |
| V2-08 | 7 | Use only LIVE and RECURRING dashboard tabs; LIVE is default | `seller.js` dashboard views | `SellerDashboardUiTest`; `SellerAppScriptStructureTest` | PASS (automated) | — |
| V2-09 | 7 | LIVE includes currently orderable one-time, recurring and preorder items | `SellerAppService`, `OfferingTiming`, `seller.js` | `SellerDashboardUiTest`; `RecurringBuyerOrderFlowTest` | PASS (automated) | — |
| V2-10 | 7 | RECURRING lists schedule defaults/next dates, not customer orders | `seller.js`, `SellerAppService` | `SellerDashboardUiTest`; live recurring tab rendered empty state | PASS (automated); LIVE SMOKE | — |
| V2-11 | 8 | Use occurrence-specific LIVE actions; no ambiguous schedule pause | `seller.js` action rendering and handlers | `SellerDashboardUiTest`; `SellerAppScriptStructureTest` | PASS (automated) | — |
| V2-12 | 9 | Manage Schedule changes future defaults without rewriting history/overrides | `RecurringScheduleService` | `RecurringFoundationPersistenceTest`; `RecurringScheduleHttpTest` | PASS (automated) | — |
| V2-13 | 10 | Edit Today/Edit This Date writes only the selected occurrence | `RecurringScheduleController`, `OccurrenceOverride` | `RecurringScheduleHttpTest`; Poha persistence scenario | PASS (automated) | — |
| V2-14 | 11 | Sold Out/Close Orders applies to one date and leaves other dates intact | occurrence status/override logic | `RecurringFoundationPersistenceTest`; `RecurringBuyerOrderFlowTest` | PASS (automated) | — |
| V2-15 | 12 | End schedules without deleting past occurrences or orders | `RecurringScheduleService` | `RecurringFoundationPersistenceTest` | PASS (automated) | — |
| V2-16 | 13 | Scope order lists, delivery updates and filters to an occurrence/date | `OrderItem`, `SellerAppService`, seller order UI | `RecurringBuyerOrderFlowTest`; `SellerDeliveryCompletionServiceTest`; `SellerFilterScriptStructureTest` | PASS (automated) | — |
| V2-17 | 14 | Show buyers the next/open occurrence and its date-specific values | `DiscoveryService`, buyer DTOs and `buyer.js` | `RecurringBuyerOrderFlowTest`; `RecurringScheduleHttpTest` | PASS (automated) | — |
| V2-18 | 14 | Revalidate selected date, cutoff, orderability and remaining quantity at checkout | `OrderService`, `OfferingTiming` | `RecurringBuyerOrderFlowTest`; `OrderServiceValidationTest` | PASS (automated) | — |
| V2-19 | 16 | Prevent duplicate occurrences/orders and preserve existing history | uniqueness, locking and order service | `RecurringFoundationPersistenceTest`; `RecurringBuyerOrderFlowTest`; `OrderNumberUniquenessTest` | PASS (automated) | — |
| V2-20 | 16 | Enforce seller ownership and role checks on schedule/occurrence actions | `RecurringScheduleController`, `SecurityConfig` | `RecurringScheduleHttpTest`; `SellerSessionGuardIntegrationTest` | PASS (automated) | — |

## Seller Dashboard V3

| ID | Source section | Expected behavior | Implementation | Test/evidence | Status | Defect |
|---|---|---|---|---|---|---|
| V3-01 | 2, 3 | Show seller greeting and only the three approved summary cards | `seller.js`, `SellerDashboardDto` | `SellerDashboardUiTest` | PASS (automated) | — |
| V3-02 | 3.1 | Total Orders is dynamic and opens existing All Orders | `seller.js` route/handler | `SellerDashboardUiTest` | PASS (automated) | — |
| V3-03 | 4 | Kitchen/Store card shows status, View and Edit using existing routes | `seller.js`, `index.html` routes | `SellerDashboardUiTest`; `SellerKitchenPreviewTest` | PASS (automated) | — |
| V3-04 | 5 | Remove duplicated dashboard quick-action row | `seller.js` | `SellerDashboardUiTest` | PASS (automated) | — |
| V3-05 | 6 | Show only LIVE and RECURRING tabs; LIVE is default | `seller.js` | `SellerDashboardUiTest`; live Seller page showed both tabs | PASS (automated); LIVE SMOKE | — |
| V3-06 | 6 | Use PRE-ORDER/RECURRING as badges, not extra dashboard filters | `seller.js` card rendering | `SellerDashboardUiTest`; `SellerAppScriptStructureTest` | PASS (automated) | — |
| V3-07 | 6, 7 | Show only orderable offerings under LIVE and schedule rules under RECURRING | `SellerAppService`, `seller.js` | `SellerDashboardUiTest`; live RECURRING empty state rendered | PASS (automated); LIVE SMOKE | — |
| V3-08 | 8 | Render contextual actions for one-time, preorder and recurring occurrences | `seller.js` | `SellerDashboardUiTest`; `SellerAppScriptStructureTest` | PASS (automated) | — |
| V3-09 | 9, 10 | Display occurrence booking/availability and “No limit” accurately | seller DTOs and `seller.js` | `SellerDashboardUiTest`; recurring persistence tests | PASS (automated) | — |
| V3-10 | 11 | Do not show an inline quantity stepper on dashboard cards | `seller.js` | `SellerDashboardUiTest`; `SellerAppScriptStructureTest` | PASS (automated) | — |
| V3-11 | 12 | Align actions consistently without adding dummy buttons | `seller.css`, `seller.js` | `SellerDashboardUiTest`; prior UI evidence in `requirements_matrix.md` | PASS (automated) | — |
| V3-12 | 13 | Provide full-width Add Offering using the existing create flow | `seller.css`, `seller.js` | `SellerDashboardUiTest` | PASS (automated) | — |
| V3-13 | 14 | Show real Confirmed Today, Pending Today and This Month values | `SellerAppService`, `SellerEarningsDto` | `SellerEarningsUiTest`; `SellerDashboardUiTest` | PASS (automated) | — |
| V3-14 | 15, 16 | Preserve bottom navigation and Society/Payment/Delivery order filters | `seller.html`, `seller.js` | `SellerFilterScriptStructureTest`; `SellerDashboardUiTest` | PASS (automated) | — |
| V3-15 | 17, 18 | Preserve loading/empty/error states and dynamic data; no backend rebuild | seller UI/services | `SellerDashboardUiTest`; full Maven verification | PASS (automated) | — |

## Live and non-functional acceptance overlay

| Check | Evidence from this snapshot | Status |
|---|---|---|
| Buyer entry route | Live Buyer home rendered in browser | LIVE SMOKE |
| Seller entry route and tabs | Live Seller dashboard rendered; LIVE and RECURRING were visible; RECURRING showed its empty state | LIVE SMOKE |
| Admin entry route | Admin sign-in page rendered; no authorized Admin workflow was exercised | NOT TESTED |
| Buyer pre-order discovery | Live Food & Kitchens loaded; no Pre-order section/item was present in the observed view | NOT TESTED (no live sample) |
| Occurrence-specific live order/checkout | No live recurring/preorder write was attempted | NOT TESTED |
| Responsive widths | Buyer/Seller/Admin were measured at the eight requested sizes; see `FINAL-E2E-TEST-REPORT.md`. The 390px target measured 391 CSS px under browser DPR 0.75 | PARTIAL |
| Original DOCX coverage | DOCX files are absent; extracted specifications were used | NOT VERIFIED beyond extracts |

The current demo was not modified for acceptance. A green automated matrix does not imply the unavailable
live buyer occurrence flow, authorized Admin workflows, or production readiness has passed.
