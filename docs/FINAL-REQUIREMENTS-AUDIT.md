# SocioMart Final Requirements Audit

Audit date: 2026-10-10. This is a demo-release audit, not a production-readiness declaration. It includes a
local feature-branch verification addendum; the branch is not yet merged or deployed.

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
| Seller Onboarding V1 and the later demo-auth instruction | The later user instruction explicitly supersedes OTP for the demo and authorizes password-based Buyer/Seller registration while preserving Admin approval and role boundaries. The source OTP criterion remains PARTIAL rather than being called complete. Stable URL aliases and the full protected-field change workflow remain unimplemented. |

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
| ONB-01, ONB-03–ONB-05, ONB-08–ONB-09, ONB-14, ONB-22 | Direct Seller registration collects required details from Admin-managed location options, persists a pending seller/storefront, keeps pending sellers out of approved Seller operations, and allows Admin approval before the seller can manage offerings; approved storefront URL is anonymously browsable | `DirectRegistrationSecurityIntegrationTest`; Playwright `seller-onboarding.spec.cjs`, `marketplace-lifecycle.spec.cjs`, `mobile-registration.mobile.spec.cjs`; passed locally | PASS |
| ONB-20, ONB-24–ONB-25 | Existing seller pause controls and buyer kitchen eligibility/error rules are implemented independently of new seller registration | `AdminV2HandoverTest`, `KitchenVisibilityEligibilityTest`, `KitchenIneligibilityMessageTest`, `Requirements1920IntegrationTest`; passed in full suite | PASS |
| ONB-02, ONB-06–ONB-07, ONB-10–ONB-12, ONB-15–ONB-17, ONB-21, ONB-23, ONB-29 | OTP is intentionally omitted under the later demo-auth instruction; slug generation/reservation race safety, full pending-publication proof, all Admin decision actions, complete first-login/profile verification, protected identity, suspension and public-page affordances are not all covered end-to-end | Direct-auth integration and browser tests cover direct registration, duplicate mobile refusal, pending status, invalid password, Admin approval and role boundaries; request-changes/reject, slug concurrency and several profile/public-page details remain unverified | PARTIAL |
| ONB-13, ONB-18–ONB-19, ONB-26–ONB-28 | Seller request-changes/resubmission, protected-field change requests, the canonical `/kitchens` route, Admin copy-link controls and old-slug aliases are not implemented or not evidenced | No end-to-end implementation/test for these outcomes | BLOCKED |

The demo registration flow does not implement the source document's OTP requirement: the newer explicit
demo instruction supersedes it, and the replacement password flow is tested locally. The local browser suite
does not verify every onboarding detail, slug race, request-changes/rejection, or Admin promotion action.

## Independent acceptance metrics

| Metric | Result |
|---|---:|
| Requirement specification files analyzed | 7 |
| Source acceptance criteria | 130 |
| Unique criteria after explicit alias normalization | 117 |
| Fully implemented and locally verified | 95 |
| Partially implemented / evidence incomplete | 14 |
| Failed in an executed acceptance test | 0 |
| Not verified | 2 |
| Blocked / not implemented in the current scope | 6 |
| Automated tests (current local branch) | Maven: 592 total; 0 failures; 0 errors; 1 skipped. Playwright: 4 passed, 0 failed. |
| Live read-only smoke checks | 5 historical checks; no live business-order journey for this branch |
| Latest GitHub `main` commit before this PR | `702679c86d450318fce2c6f08a2ac63ab775206f` |
| Current feature branch | `copilot/demo-registration-e2e`; local tests pass; not yet merged or deployed |
| Last verified Render runtime commit | `d9bb8782ff58719a68df01236c09e9e0990c5bf4` |
| Latest main CI before this PR | PASS; run `37974143064` on `702679c86d450318fce2c6f08a2ac63ab775206f` |
| GitHub/Render runtime synchronized | YES for `main`; current feature-branch changes are not yet synchronized |
| Monthly infrastructure budget | ₹0/month; existing Render Free plan, no paid resources |
| Overall completion | **PARTIAL — local validation only; PR, CI, deployment and live E2E remain** |

The status totals are calculated from the 117 canonical IDs: `95 + 14 + 0 + 2 + 6 = 117`. The six blocked
outcomes are not silently counted as passes. The V1 OTP criterion is separately recorded as PARTIAL because
the later user instruction explicitly replaces it for the demo. No completion percentage is reported.

### Historical live smoke evidence and limits — 2026-10-09

These read-only observations describe the then-deployed application, not the current unmerged feature branch.
The branch adds Buyer/Seller password registration; no live write-based smoke test has been run for it.

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
- The earlier application CI run [37963266688](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37963266688)
  passed on the runtime commit; latest `main` CI [37972517277](https://github.com/utkarshnikhare/my-first-spring-api/actions/runs/37972517277)
  also passed on the documentation-only merge. The local native PostgreSQL Testcontainers test was skipped
  because Docker is unavailable.

## Second independent pass

A second count pass re-read all seven extracted acceptance sections and checked the ID arithmetic. It found
17 Automatic Listing scenario rows, 16 Admin items, 17 Delivery items after excluding four illustrative
checkbox lines, 20 Quantity items, 15 Recurring V2 items, 16 Dashboard V3 items, and 29 Seller Onboarding
items: 130 source criteria. The alias table reduces this by exactly 13 to 117. The seven status totals above
sum to 117. This second pass does not convert live-unverified or out-of-scope items into passes.
