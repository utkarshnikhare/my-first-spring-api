# SocioMart Recurring Offerings V2 / Seller Dashboard V3 Verification Matrix

Verification date: 2026-10-09

Source limitation: the two original DOCX binaries are not present under `C:\project`. Their extracted specification text is available at `docs/REQ1_recurring_v2.txt` and `docs/REQ2_dashboard_ui_v3.txt`; this matrix maps every requirement in those extracts plus the takeover acceptance list. No requirement was inferred beyond those sources.

Latest local full Maven verification: **589 tests, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS**. The native PostgreSQL Testcontainers case was skipped locally because Docker is unavailable; the H2 PostgreSQL-mode migration/JPA validation test passed. GitHub Actions on main commit `89e57ade5bbabfe2eecd3cdddfe8d401ac1a741f` ran **589 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**, including the native PostgreSQL Testcontainers test. Original V2/V3 release baseline: **587 tests, 0 failures, 0 errors, 0 skipped**. Latest focused recurring/dashboard regression run: **66 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (25.7 seconds). `node --check` passed for all six tracked frontend JavaScript files.

Status meanings: **PASS** = implemented and covered by executable or direct source-contract evidence. **UI VERIFIED** = additionally exercised in a supported headless browser. **LIVE SMOKE** = read-only browser/API check against the deployed demo. **NOT VERIFIED** = evidence could not be produced in this environment.

## Recurring Offerings V2

| Requirement | Status | Evidence |
|---|---|---|
| Reuse existing Create Offering form | PASS | `seller.js` shared create flow builds optional `ProductCreateDto.recurringSchedule`; `RecurringScheduleCreateProductIntegrationTest` verifies atomic product/schedule creation. |
| One Time / Repeating Schedule availability | PASS | `seller.js` availability controls and `buildRecurringSchedule`; one-time regression coverage remains in `BuyerSellerUiUpdateTest`. |
| One-time Today / Tomorrow / Choose Date | PASS | Existing `offeringFor` flow retained; full suite covers current one-time behavior. |
| Repeating start date | PASS | UI validation plus `RecurringScheduleService.createSchedule`. |
| Monday-Sunday selection | PASS | UI sends `DayOfWeek` names; Manage Schedule now maps serialized names back to checked boxes. |
| This week / 1 month / Until date / Ongoing | PASS | `computeRecurringEnd`, shared recurring form, `ongoing` now included in create payload. |
| Default quantity and blank = No limit | PASS | Nullable schedule/occurrence quantity, `clearDefaultQuantity`, `clearQuantity`, and UI `No limit` copy. |
| Default close and delivery/ready time | PASS | Stored on `RecurringSchedule`, snapshotted per occurrence, resolved with per-field override precedence. |
| Schedule defaults separate from occurrences | PASS | Separate `RecurringSchedule`, `Occurrence`, and `OccurrenceOverride` entities/repositories. |
| Independent occurrence/date order buckets | PASS | `OrderItem.occurrence` and `scheduledDate`; checkout, capacity, protected edits, seller orders, and counts now scope by occurrence ID/date. |
| Per-day override isolation | PASS | `updateOccurrenceOverride`; `RecurringFoundationPersistenceTest.acceptanceScenario` and Poha regression. |
| Edit Today / Edit This Date | PASS | LIVE actions load `OccurrenceDto` including verified `productId`, prefill the existing editor, and PATCH only the occurrence. |
| Manage Schedule future defaults/pattern | PASS | PUT schedule flow reconciles future rows, preserves field-level overrides and history, and rejects ended schedules. |
| End Schedule | PASS | Future/current materialized rows are ended without deletion; schedules cannot be extended/reactivated after ending. |
| Protected edits with customer orders | PASS | Quantity cannot fall below booked, delivery cannot change, close cannot shorten; same checks apply during schedule propagation. |
| LIVE is default and means orderable now | PASS | Seller SPA resets LIVE on dashboard entry; backend resolves today or an eligible open future occurrence and excludes closed/sold-out/paused states. |
| RECURRING is schedule management only | PASS | Schedule cards show defaults/repeat pattern/next occurrence with Manage Schedule and End Schedule only. |
| Recurring LIVE contextual actions | PASS | View Orders, Edit Today/This Date, Sold Out Today/This Date, Close Orders Today/This Date; no generic recurring Pause. |
| Occurrence-specific Seller Orders | PASS | Product + occurrence date route and service predicates; `RecurringBuyerOrderFlowTest` verifies future/today separation. |
| Society, Payment, Delivery filters compose | PASS | Existing seller order filters retained; `SellerFilterScriptStructureTest` and service tests. |
| Mark All Delivered occurrence isolation | PASS | Product + date scope; drafts/cancelled excluded and idempotency covered by `SellerDeliveryCompletionServiceTest`. |
| Buyer Order Now / Pre-order | PASS | Today resolves LIVE; open future occurrence resolves PRE_ORDER with delivery date and appears in storefront/marketplace Pre-order sections. |
| Correct resolved date, quantity, close, ready | PASS | Buyer/seller DTO decoration uses resolved occurrence values; Poha tests verify `25 / 10:00 / 14:00`. |
| Server-side checkout validation | PASS | Explicit buyer `scheduledDate` is honored; stale/invalid/closed dates, flags, cutoff, and capacity are revalidated at draft and placement. |
| 90-day Ongoing cap | PASS | Server owns inclusive 90-day horizon; UI sends `ongoing:true`. |
| Near-expiry prompt and safe extension | PASS | <=14-day Extend Schedule prompt, ownership-checked endpoint, active/ongoing guards, idempotent occurrence generation. |
| History and order preservation | PASS | Rows/overrides/orders are retained across schedule edits/end; no recurring-order subsystem was introduced. |
| Duplicate occurrence prevention | PASS | Unique schedule/date constraint, locked generation, existing-date set, retry/idempotency tests. |
| Authorization and ownership | PASS | Seller role plus product/schedule/occurrence ownership checks on controller routes; security/full-suite coverage. |

## Wednesday Poha Acceptance

| Step | Status | Evidence |
|---|---|---|
| Mon/Wed/Fri defaults = quantity 14, close 10:00, delivery 13:00 | PASS | `RecurringFoundationPersistenceTest.pohaAcceptanceOverrideFutureDefaultsPastHistoryAndEndAreIsolated`. |
| Wednesday override = quantity 25, delivery 14:00 only | PASS | Same test and `acceptanceScenario`. |
| Wednesday close remains 10:00 | PASS | Explicit assertions before and after Manage Schedule. |
| Monday/Friday initially remain 14 / 10:00 / 13:00 | PASS | `acceptanceScenario`. |
| Manage Schedule future defaults = 18 / 10:00 / 13:30 | PASS | Poha regression verifies future Monday/Friday and past snapshots. |
| Wednesday override remains 25 / 10:00 / 14:00 | PASS | Explicit post-update assertions. |
| Duplicate generation prevented and end preserves rows/override | PASS | Idempotent generation and final history assertions. |

## Seller Dashboard V3

| Requirement | Status | Evidence |
|---|---|---|
| Header and dynamic greeting | PASS | `sellerHomeView`, authenticated seller name, time-based greeting. |
| Views Today | PASS | Real menu/storefront/product view analytics for the seller kitchen; no sample value. |
| Followers | PASS | `FavouriteRepository.countByKitchen`. |
| Total Orders dynamic and full card opens All Orders | PASS | Non-draft count; card links to existing `#/orders` and handler selects all. |
| Compact Kitchen/Store card | PASS | Image, name, truthful active/paused/unavailable state, View and Edit only. |
| Buyer-facing View Store | PASS | Existing `/index.html#/kitchen/{id}` preview route. |
| Existing Edit Store | PASS | Existing `#/kitchen` route. |
| Duplicate quick-action row removed | PASS | Not called by dashboard; skeleton row also removed. |
| LIVE / RECURRING tabs only | PASS | Two dashboard tabs; LIVE resets when entering Home. |
| PRE-ORDER and RECURRING badges | PASS | Card badge logic retained without extra dashboard filters. |
| Currently orderable visibility | PASS | Backend occurrence timing/status plus client closed/paused/sold-out filtering; future recurring discovery fixed. |
| Contextual actions | PASS | One-time and recurring branches render only applicable controls. |
| Quantity / No limit wording | PASS | Resolved remaining/booked values; null displays `No limit`. |
| No inline quantity stepper | PASS | Dashboard card contains no increment/decrement control; static regression guard. |
| Card/button alignment | PASS | Shared `sd-card__actions`/`sd-btn` tokens; recurring small/danger styles added. |
| Full-width Add Offering | PASS | `sd-add` width 100%, existing create route. |
| Earnings Summary: three aligned columns | PASS | CSS grid with Confirmed Today, Pending Today, This Month. |
| Real financial values and accurate labels | PASS | Backend paid/pending order values; labels avoid claiming verified settlement revenue. |
| Existing bottom navigation | PASS | Home / Store / Orders / My Offerings / Earnings unchanged. |
| Existing Orders filters | PASS | Society, Payment, Delivery filter UI and service composition retained. |
| Loading, empty, and error states | PASS | Dashboard skeleton, empty states, retry state, and truthful store-fetch failure state. |
| Mobile layout | UI VERIFIED | Chrome headless 390x844 seller/buyer captures; no horizontal document overflow; cards/actions remain usable and bottom navigation remains fixed. |

## Integration / Regression Evidence

| Area | Status | Evidence |
|---|---|---|
| Seller creates one-time and recurring offerings | PASS | Seller create tests plus atomic recurring integration. |
| Schedule and occurrences persist | PASS | Persistence and HTTP integration tests. |
| Buyer checkout uses selected occurrence | PASS | Explicit selected-date regression and placement revalidation. |
| Seller occurrence orders and filters | PASS | Buyer flow, seller order detail, filter, and delivery completion tests. |
| One-time Seller/Buyer/Admin regressions | PASS | Complete Maven suite (result below). |
| JavaScript syntax | PASS | `node --check src/main/resources/static/js/seller.js` and `buyer.js`. |
| Desktop headless browser | UI VERIFIED | Chrome headless 1440x1100 dashboard render: metrics, store, LIVE/RECURRING tabs, filters, aligned cards/actions, and fixed navigation visible. Screenshot: `C:\Users\Admin\AppData\Local\Temp\kilo\sociomart-seller-desktop-retry.png`. |
| Mobile headless browser | UI VERIFIED | Chrome headless 390x844 seller and buyer renders plus CDP interaction: RECURRING tab, no generic Pause Schedule, Create Offering availability choices, and no horizontal document overflow. Screenshots: `sociomart-seller-mobile.png`, `sociomart-buyer-mobile.png` in the approved temp directory. |
| Buyer checkout through browser UI | NOT VERIFIED | Checkout behavior is verified at service/controller integration level, including explicit occurrence selection and placement revalidation, but the complete payment UI was not submitted by browser automation. |

## Persistence Migration Preparation

| Check | Status | Evidence |
|---|---|---|
| Isolated H2/PostgreSQL-mode schema migration | PASS | Flyway V1 ran on an isolated in-memory H2 database; Hibernate `ddl-auto=validate` verified the migration against all JPA entities; table and foreign-key checks passed. |
| Native PostgreSQL migration test | PASS (GitHub CI) | Testcontainers started PostgreSQL on the Docker-enabled GitHub runner; V1 applied and Hibernate validated the JPA schema. Docker is unavailable locally, so the local run skipped this test. |
| Existing Render data export | BLOCKED | Render demo uses in-memory H2. The public H2 Console refuses remote connections, and no complete read-only export endpoint exists. The existing service was not restarted, its environment was not changed, and no external database was provisioned. |
| Persistent demo profile | PREPARED, NOT DEPLOYED | `postgres-demo` uses Flyway, Hibernate validation, explicit JDBC environment values, no automatic seed, and demo auth. Render remains on its original H2 `demo` profile. |

## Post-release Live Verification (2026-10-09)

| Check | Status | Evidence |
|---|---|---|
| Render deployment | PASS | Existing `sociomart-demo` service, `main` branch, deployment `dep-db4e7uu0tbcc73e38o7g`, commit `755e98922e16bc9b412b2eb653ed0517def14a42`; Render reported DEPLOYED. |
| Application/API health | LIVE SMOKE | `GET /api/kitchens` and `GET /api/auth/config` returned HTTP 200 after deployment. |
| Deployed frontend version | PASS | Live `seller.js`, `buyer.js`, and `admin.js` matched the corresponding files in merge commit `755e989` byte-for-byte. |
| Seller LIVE/RECURRING navigation | LIVE SMOKE | LIVE is the default; RECURRING opens its schedule view. The demo seller currently has no recurring schedules. |
| Repeating-schedule form | LIVE SMOKE | One-time/repeating choices, weekdays, duration choices, and the 90-day Ongoing copy rendered. The form was not submitted. |
| Buyer discovery | LIVE SMOKE | Food & Kitchens loaded. Public discovery returned 74 items and 15 kitchens; none had preorder, recurring, or next-occurrence fields set. |
| Buyer occurrence checkout | NOT VERIFIED | No live recurring/preorder offering exists in the demo seed. No order or schedule was created on the public service. Isolated local flow tests passed. |
| Admin features | NOT VERIFIED | The shared browser session was a Seller account; the Admin page correctly denied that role. Admin workflows were not exercised with an Admin account. |
| Responsive measurement | NOT VERIFIED | No overflow was reported at configured 1440/390 widths, but the browser reported CSS client widths scaled by 1.5, so exact viewport certification is inconclusive. |

## Known Verification Limits

- Original DOCX binaries are unavailable; verification uses their locally extracted text files.
- Concurrency is protected with pessimistic locks and database uniqueness, but no simultaneous-thread browser acceptance test exists for recurring creation.
- Browser evidence is limited to local headless Chrome; cross-browser Safari/Firefox rendering is not verified.
- Complete seller form submission and buyer payment submission were not automated through the browser; their API/service paths are covered by the green integration suite.
- Render's demo profile uses in-memory H2. A service restart reseeds demo data; non-seed runtime data cannot be guaranteed to persist through a deploy. No explicit database reset or environment-variable change was made.
