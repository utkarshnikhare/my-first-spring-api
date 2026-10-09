package com.example.my_first_spring_api.model;

/**
 * Lifecycle status of a {@link RecurringSchedule}.
 *
 * <p>Phase 1 (data/model foundation only): exactly the two states the V2/V3
 * documents require. A schedule is ACTIVE while it may still produce
 * occurrences; ENDED stops all future occurrences while preserving history
 * (occurrences, overrides and orders already placed are never deleted).
 */
public enum RecurringScheduleStatus {
    ACTIVE,
    ENDED
}
