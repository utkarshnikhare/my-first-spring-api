package com.example.my_first_spring_api.model;

/**
 * Lifecycle of one stored {@link Occurrence} (one selling date of a
 * {@link RecurringSchedule}).
 *
 * <p>Semantics reuse the existing offering lifecycle vocabulary where possible:
 * LIVE / ORDERS_CLOSED / SOLD_OUT mirror the product-level badge states the
 * seller dashboard already renders (see {@code seller.js} badge logic), while
 * SCHEDULED covers a materialised current/future occurrence; whether its
 * preorder window is open is resolved from the product timing rules. ENDED,
 * ORDERS_CLOSED and SOLD_OUT are never orderable.
 *
 * <p>Per-day sold-out / paused flags live on {@link Occurrence#isSoldOut()} /
 * {@link Occurrence#isOrdersPaused()}; this status is the lifecycle projection
 * used for filtering and display in later phases.
 */
public enum OccurrenceStatus {
    SCHEDULED,
    LIVE,
    ORDERS_CLOSED,
    SOLD_OUT,
    ENDED
}
