package com.example.my_first_spring_api.model;

/**
 * How a future (pre-order) offering's availability window works.
 * FIXED  — one fixed availability date (e.g. "tomorrow"), single cutoff.
 * FLEXIBLE — buyer picks any date inside [availableDate, availableUntilDate]
 *            plus a time slot; each date must respect the offering cutoff.
 */
public enum PreorderType {
    FIXED,
    FLEXIBLE
}
