
## 2.2 Frontend (seller.js) — redesigned (approved dashboard sd language)

Key functions/patterns reviewed: `sellerDate`, `sellerApi`, `formVals`, `availabilityOption`, `sdOfferingFilterHtml`, `sdCategoryGroup`, `sdCategoryLabel`, `submitOfferingEdit`, `openModal/closeModal`, `sellerRender`, `offeringDetailUrl`, `sellerOrderDetailView`, `sellerHomeView`, `sellerQuickPostView`. Date-tab UI: `data-action="set-date"`, `set-availability`, `set-availability-date`, `set-delivered`, `set-dash-filter`, `set-offering-society/status/delivery`.

### 2.3 Requirements baseline (context summary)

- Done: 2 requirement docs + baseline mapped; SellerService/FeatureService/OfferingTiming/frontend JS integration points reviewed; no GlobalExceptionHandler edits planned.
- Blocking condition: explicit go-ahead + finalized backend/frontend sequence.

# CONTINUATION NOTE — Recurring Schedule System (seller my-offerings)

**Session:** TASK RESUMPTION (continuing previous context-work session)
**Repo root (git):** c:\project\my-first-spring-api
**Spring Boot module:** c:\project\my-first-spring-api\my-first-spring-api
**Branch:** delivery-admin-v1 (HEAD 5f5968a)
**Date:** 2026-10-07

---

## 1. Objective

Implement the **Recurring Schedule (daily offering with per-date overrides)** feature for the seller "My Offerings" screen, on top of the existing seller dashboards / orders / history UI. Core domain rule: each selling date is an independent **Occurrence**; a Wednesday override must NOT affect Monday/Friday.

## 2. Verified current state

### 2.1 Backend (Spring Boot) — FILES COMPLETED (untracked, not committed)

The following new files exist in the working tree and compile:

- `src/main/java/.../model/RecurringSchedule.java` (entity: productId, startDate, endDate, recurrenceDays CSV of ISO weekday numbers, defaultQuantity, defaultOrderCloseTime, defaultReadyByTime, status, timestamps)
- `src/main/java/.../model/Occurrence.java` (entity: schedule, occurrenceDate, quantity, orderCloseTime, readyByTime, status, soldOut, ordersPaused — UniqueConstraint (schedule_id, occurrence_date))
- `src/main/java/.../model/OccurrenceOverride.java` (per-day override: quantity, orderCloseTime, readyByTime, soldOut, ordersPaused; null = inherit)
- `src/main/java/.../model/{OccurrenceStatus,RecurringScheduleStatus}.java`
- Repositories: `RecurringScheduleRepository`, `OccurrenceRepository`, `OccurrenceOverrideRepository`
- Service: `RecurringScheduleService` (createSchedule -> generateOccurrences for each weekday in [start,end]; getSellerSchedules; updateOccurrenceOverride; getResolvedOccurrence)
- Controller: `RecurringScheduleController` (POST /api/seller/schedules/{productId}, POST .../{scheduleId}/end, GET .../occurrences/{occurrenceId}, PATCH .../occurrences/{occurrenceId})
- DTOs: `RecurringScheduleDto`, `OccurrenceDto`
- Tests: `RecurringFoundationPersistenceTest`, `SchemaProbeTest`

- **mvnw compile: EXIT=0 (clean).**

## 5. Approvals needed (before I begin edits)

1. Confirm the P0a–P2 sequence above.
2. Confirm backend-first ordering (OfferingTiming + RecurringSchedule core → SellerService → controller → seller.js).
3. Confirm what to do about the currently failing structural test `BuyerSellerUiUpdateTest.myOfferingsPageReusesTheExistingAddOfferingFlow` (fix seller.js to restore ordering vs. keeping the test as a pin).

## 6. Open risks / notes

- The full-suite red is exactly one test (structural seller.js test). It may be a pre-existing pin vs. the redesigned seller.js; do not silently "fix" the test without user approval.
- Recurring creation must be atomic; use a single @Transactional boundary in SellerService.
- seller.js already uses `sellerDate(S.selectedDate)` scoping (`offeringDetailUrl`), so new UI should build on that pattern, not invent new routes.


## 4. Proposed implementation sequence (BACKEND-FIRST, then FRONTEND)

| Phase | Scope | Files | Acceptance |
|-------|-------|-------|------------|
| P0a | Domain (OfferingTiming) guards | OfferingTiming.java | occurrences independent per date; validation passes current tests incl. schema probe |
| P0b | Recurring core | RecurringSchedule.*, Repositories, RecurringScheduleService | createSchedule + generateOccurrences materializes rows; persistence tested |
| P0c | API + atomic wiring | RecurringScheduleController, SellerService.createProduct | POST schedule endpoint + createProduct ties schedule/occurrences; existing unit tests pass |
| P1 | SellerService + OfferingTiming domain rules for overrides | SellerService.java, OfferingTiming.java | Wednesday override does not touch Monday/Friday; integration unit tests pass |
| P2 | Frontend | seller.js (+ seller.html if needed) | date tabs / override UI functional; existing UI unit tests pass; pinned patterns intact |

2. `OfferingTiming` domain rule — each selling date is an independent occurrence; Wednesday override must not affect Monday/Friday (enforced by Occurrence uniqueness + isolation; also honor recurring schedule defaults).
3. Ensure `RecurringScheduleService` is wired sanely with `SellerService` (atomicity).


## 3. Remaining work

**Backend (must come first):**
1. `SellerService.createProduct(...)` — atomically integrate recurring creation (attach schedule + generate occurrences) when the seller provisions a recurring offering config.

- **Domain tests (RecurringFoundationPersistenceTest, SchemaProbeTest, OfferingTimingTest): pass (EXIT=0).**
- **Full unit suite: 560 tests — 559 pass, 1 FAIL** (`BuyerSellerUiUpdateTest.myOfferingsPageReusesTheExistingAddOfferingFlow`, a static seller.js structural test).

### 2.2 Frontend (seller.js) — redesigned (approved dashboard sd language)

Key functions and patterns present and under review:
- `sellerDate(dateKey)`, `sellerApi(path, opts)`, `formVals(form)`, `availabilityOption(...)`, `sdOfferingFilterHtml(...)`, `sdCategoryGroup(...)`, `sdCategoryLabel(...)`, `submitOfferingEdit(form)`, `openModal/closeModal`, `sellerRender`, `offeringDetailUrl`, `sellerOrderDetailView`, `sellerHomeView`, `sellerQuickPostView`.
- Date-tab UI: `data-action="set-date"` (today/tomorrow/pick), `set-availability`/`set-availability-date`, `set-delivered`, `set-dash-filter`, `set-offering-society/status/delivery`.

### 2.3 Requirements baseline (context summary)

- Done: 2 requirement docs + baseline mapped; SellerService/FeatureService/OfferingTiming/frontend JS integration points reviewed; GlobalExceptionHandler no changes planned.
- Blocking condition (from prior session): explicit go-ahead + finalized backend/frontend sequence.
