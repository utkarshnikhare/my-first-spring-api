# SocioMart Final Requirements Audit

Audit date: 2026-10-09. This is a demo-release audit, not a production-readiness declaration.

## Scope and counting rule

Seven user-provided specification extracts and the additional acceptance prompt were reviewed. The original
DOCX files are read-only user materials; only their extracted text was available in session scratch and is not
committed to this repository. Requirement IDs below follow the order of the explicit acceptance scenarios or
checklist items in each source. Each ID maps to one source criterion; duplicate IDs remain traceable as aliases
to a single canonical requirement rather than being silently discarded.

| Source document | IDs | Source criteria |
|---|---:|---:|
| `SocioMart_Automatic_Listing_and_Recurring_Lifecycle_V1` | AUTO-01–AUTO-17 | 17 |
| `SocioMart_Admin_App_V1_Functional_Developer_Handover` | ADMIN-01–ADMIN-16 | 16 |
| `SocioMart_Delivery_Tracking_Developer_Handover_v2` | DEL-01–DEL-17 | 17 |
| `SocioMart_Item_Quantity_and_Sold_Out_Logic_V1` | QTY-01–QTY-20 | 20 |
| `SocioMart_Recurring_Offerings_Per_Day_Overrides_V2` | REC-01–REC-15 | 15 |
| `SocioMart_Seller_Dashboard_UI_Modifications_V3` | DASH-01–DASH-16 | 16 |
| `SocioMart_Seller_Onboarding_Approval_and_Public_URL_Logic_V1` | ONB-01–ONB-29 | 29 |
| **Total** |  | **130 source criteria** |

Four checked/unchecked delivery examples are illustrative UI rows, not acceptance criteria. The 130 count is
17 automatic-listing scenarios plus 113 actual checklist items. Ten duplicate clusters below normalize the
130 source IDs to **117 unique acceptance outcomes** (13 duplicate source IDs). Conceptual overlap alone was
not enough to merge requirements; separate actors, lifecycle states, or expected outcomes remain separate.

## Cross-document version and overlap decisions

| Relationship | Audit decision |
|---|---|
| Automatic Listing V1 and Recurring Offerings V2 | V2 supplies the more detailed schedule/default/occurrence override contract. Keep V1's time-bound opening, cutoff, expiry, missed-job and history scenarios; do not treat them as replaced. |
| Recurring Offerings V2 and Seller Dashboard V3 | V3 supersedes the older dashboard filter/layout proposal with LIVE and RECURRING. Retain V2's backend occurrence, schedule, order-bucket and edit-scope rules. Shared tab/actions checks are aliases below. |
| Quantity V1 and recurring specifications | Capacity is occurrence-specific; a schedule's default quantity does not create shared weekly inventory. The three overlapping per-occurrence checks are one canonical outcome with all source IDs preserved. |
| Delivery Tracking V2 and seller/admin order screens | Delivery is a separate persisted order state from payment. Seller occurrence filters and Admin monitoring remain distinct screens even where they expose the same status. |
| Admin V1 and Seller Onboarding V1 | Approval actions overlap, but Onboarding additionally requires OTP registration, application state and stable public identity. The approval action is one shared outcome; the onboarding lifecycle remains separately tracked. |
| Seller Onboarding V1 and the approved demo scope | The source requires OTP onboarding and reserved slug routes; this release explicitly excludes authentication migration. Those unmet items are BLOCKED, not represented as completed. Current storefront paths use kitchen IDs rather than the required stable public slug workflow. |

### Duplicate source IDs retained as canonical aliases

| Canonical outcome | Source IDs mapped to it | Duplicate IDs removed from the unique count |
|---|---|---:|
| An occurrence-only edit leaves other dates and schedule defaults unchanged | AUTO-09, REC-05, QTY-18 | 2 |
| Unlimited quantity does not falsely sell out or use finite-stock decrement logic | AUTO-11, QTY-19 | 1 |
| A date's stock/order bucket is independent of other recurring dates | AUTO-12, REC-03, QTY-17 | 2 |
| LIVE is the default orderable-occurrence view | REC-08, DASH-05, DASH-06 | 2 |
| RECURRING is schedule management, not order processing | REC-09, DASH-08 | 1 |
| Recurring/Pre-order are badges, not additional tabs | REC-10, DASH-07 | 1 |
| Recurring LIVE actions are scoped to the selected occurrence | REC-11, DASH-09 | 1 |
| Schedule-level controls are Manage/End, not ambiguous Pause/Sold Out | REC-12, DASH-10 | 1 |
| Seller Orders retains the Society/Payment/Delivery filter set | REC-14, DASH-16 | 1 |
| Admin approve/reject/request-changes action set | ADMIN-03, ONB-12 | 1 |
| **Total duplicate source IDs** |  | **13** |

## Requirement-to-code/test traceability

`PASS` means the stated implementation has local automated-test evidence; it does not imply the business
transaction was replayed against Render. `PARTIAL` means relevant code exists but an acceptance detail or
test is incomplete. `NOT VERIFIED` means no determinative test evidence was found. `BLOCKED` means the
feature is outside the explicitly approved demo-release scope. `FAILED` is reserved for a tested criterion
whose implementation demonstrably fails; none of the executed checks produced such a result.

| Requirement IDs | Code implementation / evidence | Test case and result | Final status |
|---|---|---|---|
| AUTO-01–AUTO-15 | Recurring schedule/occurrence service and Seller/Buyer discovery in [`seller.js`](../my-first-spring-api/src/main/resources/static/js/seller.js), [`buyer.js`](../my-first-spring-api/src/main/resources/static/js/buyer.js), and the recurring service layer | `RecurringScheduleCreateProductIntegrationTest`, `RecurringFoundationPersistenceTest`, `RecurringBuyerOrderFlowTest`, `OfferingTimingTest`; passed in full suite | PASS |
| AUTO-16–AUTO-17 | Recovery/query behavior and loading/error states exist in the lifecycle/UI surfaces; no acceptance-specific evidence was found for scheduler outage recovery or unavailable-API behavior | No dedicated missed-job/API-outage acceptance test identified | NOT VERIFIED |
| ADMIN-01–ADMIN-16 | Admin dashboard, filters, seller/buyer controls, location master, exports, audit, retention and role scoping in the Admin API/SPA | `AdminV1ScopeTest`, `AdminV2HandoverTest`, `AdminHandoverGapTest`, `AdminLocationsManagementTest`, `AdminLocationMasterHttpTest`, `AdminSystemHealthTest`; passed in full suite | PASS |
| DEL-01–DEL-16 | Persisted common-order delivery state, occurrence-scoped seller actions, buyer-facing status and composed filters | `SellerDeliveryCompletionServiceTest`, `SellerDeliveryProgressSummaryTest`, `SellerOrderDetailUiTest`, `SellerIndividualOrderUiTest`, `SellerFilterScriptStructureTest`; passed in full suite | PASS |
| DEL-17 | Checkbox error recovery is part of the acceptance surface, but a dedicated failure-injection/browser acceptance result was not found | Full suite passed; failure-state behavior not independently verified | PARTIAL |
| QTY-01–QTY-11, QTY-13–QTY-20 | Server-side stock validation, order transaction, payment-independent reservation, restoration on cancellation, no-limit mode, and occurrence-specific capacity | `DemoMarketplaceE2ETest`, `OrderServiceValidationTest`, `OrderServicePaymentTest`, `InventoryRestorationIntegrationTest`, `RecurringFoundationPersistenceTest`; passed in full suite | PASS |
| QTY-12 | Order placement uses database locking/transactional protection, but no simultaneous-buyer acceptance test was found | No concurrent final-unit test was run | PARTIAL |
| REC-01–REC-15 | Schedule defaults, materialized occurrences, date overrides, order buckets, cutoff validation, schedule end and extension | `RecurringScheduleCreateProductIntegrationTest`, `RecurringScheduleHttpTest`, `RecurringFoundationPersistenceTest`, `RecurringBuyerOrderFlowTest`; passed in full suite | PASS |
| DASH-01–DASH-16 | LIVE/RECURRING dashboard, dynamic summary data, contextual actions, alignment and existing navigation | `SellerDashboardUiTest`, `SellerAppScriptStructureTest`, `SellerFilterScriptStructureTest`; passed in full suite; desktop/mobile UI smoke evidence is recorded in [`requirements_matrix.md`](../my-first-spring-api/requirements_matrix.md) | PASS |
| ONB-20, ONB-24–ONB-25 | Existing seller pause controls and buyer kitchen eligibility/error rules are implemented independently of new seller registration | `AdminV2HandoverTest`, `KitchenVisibilityEligibilityTest`, `KitchenIneligibilityMessageTest`, `Requirements1920IntegrationTest`; passed in full suite | PASS |
| ONB-01, ONB-04–ONB-05, ONB-12, ONB-14, ONB-17, ONB-21–ONB-23, ONB-29 | Existing demo sign-in, location/service-area, approval, profile and ID-based storefront pieces cover only part of the requested registration/public-identity lifecycle | `SellerServiceAreaTest`, Admin approval tests, kitchen visibility/eligibility tests; related pieces pass, complete onboarding flow does not exist | PARTIAL |
| ONB-02–ONB-03, ONB-06–ONB-11, ONB-13, ONB-15–ONB-16, ONB-18–ONB-19, ONB-26–ONB-28 | OTP registration, application/resubmission states, backend slug reservation/aliasing, protected-field change requests, `/kitchen/{slug}`, and the onboarding URL-management workflow are not implemented as specified | No complete seller-registration/slug-flow test; current demo login and kitchen-ID route are not substitutes | BLOCKED |

The tests for onboarding-adjacent kitchen eligibility do not prove OTP registration, slug uniqueness, or
application approval. The corresponding requirement IDs remain PARTIAL or BLOCKED rather than inheriting
those unrelated passes.

## Independent acceptance metrics

| Metric | Result |
|---|---:|
| Requirement specification files analyzed | 7 |
| Source acceptance criteria | 130 |
| Unique criteria after explicit alias normalization | 117 |
| Fully implemented and locally verified | 87 |
| Partially implemented / evidence incomplete | 12 |
| Failed in an executed acceptance test | 0 |
| Not verified | 2 |
| Blocked by approved release scope | 16 |
| Automated tests | 589 total; 0 failures; 0 errors; 1 Docker-gated skip; BUILD SUCCESS |
| Live read-only smoke checks | 5; no full live business-order journey |
| GitHub application `main` commit | `d9bb8782ff58719a68df01236c09e9e0990c5bf4` |
| Render deployed commit | `d9bb8782ff58719a68df01236c09e9e0990c5bf4` |
| GitHub/Render synchronized at application release | YES |
| Monthly infrastructure budget | ₹0/month; existing Render Free plan, no paid resources |
| Overall completion | **PARTIAL** |

The status totals are calculated from the 117 canonical IDs: `87 + 12 + 0 + 2 + 16 = 117`. The 16 blocked
items are not silently counted as passes; implementing them requires a separately approved seller-onboarding
and authentication scope. No completion percentage is reported.

### Live smoke evidence and limits

- Render dashboard showed the existing `sociomart-demo` service on Free, with deployment
  `dep-db4i1rqj9qps73alfiug` for the same application SHA as GitHub `main`.
- `/api/kitchens` and `/api/auth/config` returned HTTP 200; 16 seeded kitchens and demo login configuration
  were present.
- Buyer search for `Puran Poli (Pre-order)` displayed one Aarti Kitchen occurrence for 2026-10-12; the exact
  read-only discovery API query returned the same occurrence.
- Seller LIVE and RECURRING tabs and Buyer/Seller/Admin entry routes loaded. The Admin sign-in page is not
  evidence of Admin workflow acceptance.
- No live order, schedule edit, delivery mutation, or payment was submitted. The full Buyer → order → stock →
  seller fulfilment → buyer history lifecycle has local integration evidence, not live Render E2E evidence.
- CI run [37963266688](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37963266688) passed
  on the application commit. The local native PostgreSQL Testcontainers test was skipped because Docker is
  unavailable.

## Second independent pass

A second count pass re-read all seven extracted acceptance sections and checked the ID arithmetic. It found
17 Automatic Listing scenario rows, 16 Admin items, 17 Delivery items after excluding four illustrative
checkbox lines, 20 Quantity items, 15 Recurring V2 items, 16 Dashboard V3 items, and 29 Seller Onboarding
items: 130 source criteria. The alias table reduces this by exactly 13 to 117. The seven status totals above
sum to 117. This second pass does not convert live-unverified or out-of-scope items into passes.
