package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted unit tests for the parsing helpers in {@link OfferingTiming}.
 * <p>
 * The main objective is to confirm that the helper correctly builds an ISO
 * datetime from a pure time string when supplied with an offering date.
 * It also verifies that the {@code rebaseReadyByTime} method correctly
 * adjusts date components when the supplied ISO value is older than the
 * offering date.
 */
class OfferingTimingParseTest {

    @Test
    @DisplayName("parseReadyByTime should accept 24‑hour time and prepend date")
    void parse24HourTime() {
        LocalDate date = LocalDate.of(2026, 10, 18);
        LocalDateTime result = OfferingTiming.parseReadyByTime("12:00", date);
        assertEquals("2026-10-18T12:00", result.toString());
    }

    @Test
    @DisplayName("parseReadyByTime should accept 12‑hour time with AM/PM")
    void parse12HourTime() {
        LocalDate date = LocalDate.of(2026, 10, 18);
        LocalDateTime result = OfferingTiming.parseReadyByTime("9:30 PM", date);
        assertEquals("2026-10-18T21:30", result.toString());
    }

    @Test
    @DisplayName("rebaseReadyByTime should shift old date to new offering date")
    void rebaseOldDate() {
        String rebased = OfferingTiming.rebaseReadyByTime("2026-10-17T10:00", LocalDate.of(2026, 10, 19));
        assertEquals("2026-10-19T10:00", rebased);
    }

    @Test
    @DisplayName("rebaseReadyByTime should leave ISO unchanged if date matches")
    void rebaseNoChange() {
        String unchanged = OfferingTiming.rebaseReadyByTime("2026-10-19T15:45", LocalDate.of(2026, 10, 19));
        assertEquals("2026-10-19T15:45", unchanged);
    }
}
