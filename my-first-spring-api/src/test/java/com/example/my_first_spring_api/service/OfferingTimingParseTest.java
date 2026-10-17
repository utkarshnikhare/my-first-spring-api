package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    @Test
    @DisplayName("rebaseReadyByTime should keep a readyBy that already falls after the offering date")
    void rebaseKeepsLaterReadyByDate() {
        String unchanged = OfferingTiming.rebaseReadyByTime("2026-10-20T12:00", LocalDate.of(2026, 10, 19));
        assertEquals("2026-10-20T12:00", unchanged);
    }

    // ===================== rebaseReadyByTimeForOffering =====================

    @Test
    @DisplayName("for-offering rebase corrects a one-day-early timestamp (late-night skew)")
    void forOfferingCorrectsOneDaySkew() {
        String rebased = OfferingTiming.rebaseReadyByTimeForOffering(
                "2026-10-18T00:30", LocalDate.of(2026, 10, 19));
        assertEquals("2026-10-19T00:30", rebased);
    }

    @Test
    @DisplayName("for-offering rebase heals multi-day skew while keeping the time-of-day")
    void forOfferingCorrectsMultiDaySkew() {
        String rebased = OfferingTiming.rebaseReadyByTimeForOffering(
                "2026-10-15T23:30", LocalDate.of(2026, 10, 19));
        assertEquals("2026-10-19T23:30", rebased);
    }

    @Test
    @DisplayName("for-offering rebase keeps a next-day readyBy for a today offering (e2e cross-day case)")
    void forOfferingKeepsNextDayReadyBy() {
        String unchanged = OfferingTiming.rebaseReadyByTimeForOffering(
                "2026-10-20T12:00", LocalDate.of(2026, 10, 19));
        assertEquals("2026-10-20T12:00", unchanged);
    }

    @Test
    @DisplayName("for-offering rebase anchors a bare time to the offering date")
    void forOfferingAnchorsBareTime() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String rebased = OfferingTiming.rebaseReadyByTimeForOffering("13:00", tomorrow);
        assertEquals(LocalDateTime.of(tomorrow, LocalTime.of(13, 0)).toString(), rebased);
    }

    @Test
    @DisplayName("for-offering rebase leaves a bare time that already matches the offering date")
    void forOfferingKeepsMatchingBareTime() {
        LocalDate today = LocalDate.now();
        assertEquals("13:00", OfferingTiming.rebaseReadyByTimeForOffering("13:00", today));
    }

    @Test
    @DisplayName("for-offering rebase preserves explicit 'today' intent for strict validation")
    void forOfferingPreservesTodayKeyword() {
        String value = "11:00 AM today";
        assertEquals(value, OfferingTiming.rebaseReadyByTimeForOffering(value, LocalDate.now().plusDays(1)));
    }

    @Test
    @DisplayName("for-offering rebase preserves explicit 'tomorrow' intent for strict validation")
    void forOfferingPreservesTomorrowKeyword() {
        String value = "1:00 PM tomorrow";
        assertEquals(value, OfferingTiming.rebaseReadyByTimeForOffering(value, LocalDate.now().plusDays(3)));
    }

    @Test
    @DisplayName("for-offering rebase passes null and blank through untouched")
    void forOfferingPassesThroughBlank() {
        assertNull(OfferingTiming.rebaseReadyByTimeForOffering(null, LocalDate.now()));
        assertEquals(" ", OfferingTiming.rebaseReadyByTimeForOffering(" ", LocalDate.now()));
    }

    @Test
    @DisplayName("for-offering rebase returns malformed input unchanged for the validator to report")
    void forOfferingLeavesMalformedInputUnchanged() {
        String garbage = "not-a-datetime";
        assertEquals(garbage, OfferingTiming.rebaseReadyByTimeForOffering(garbage, LocalDate.now().plusDays(1)));
        String badHour = "25:00";
        assertEquals(badHour, OfferingTiming.rebaseReadyByTimeForOffering(badHour, LocalDate.now().plusDays(1)));
    }

    // ===================== Timezone offset & UTC tests =====================

    @Test
    @DisplayName("parseReadyByTime should correctly convert UTC Z timestamp to Asia/Kolkata local time")
    void parseUtcZToKolkata() {
        LocalDate date = LocalDate.of(2026, 10, 18);
        // 12:30 UTC is 18:00 IST (+5:30)
        LocalDateTime result = OfferingTiming.parseReadyByTime("2026-10-18T12:30:00Z", date);
        assertEquals("2026-10-18T18:00", result.toString());
    }

    @Test
    @DisplayName("parseReadyByTime should parse ISO timestamp with explicit +05:30 offset")
    void parseIsoWithOffset() {
        LocalDate date = LocalDate.of(2026, 10, 18);
        LocalDateTime result = OfferingTiming.parseReadyByTime("2026-10-18T20:45:00+05:30", date);
        assertEquals("2026-10-18T20:45", result.toString());
    }

    @Test
    @DisplayName("for-offering rebase normalises UTC Z timestamp onto offering date in Asia/Kolkata")
    void rebaseUtcTimestampForOffering() {
        LocalDate offeringDate = LocalDate.of(2026, 10, 19);
        // 12:30 UTC on 2026-10-17 is 18:00 IST on 2026-10-17 (older than offering date 2026-10-19)
        String rebased = OfferingTiming.rebaseReadyByTimeForOffering("2026-10-17T12:30:00Z", offeringDate);
        assertEquals("2026-10-19T18:00", rebased);
    }

    @Test
    @DisplayName("for-offering rebase normalises offset timestamp and converts to local datetime")
    void rebaseOffsetTimestampForOffering() {
        LocalDate offeringDate = LocalDate.of(2026, 10, 19);
        String rebased = OfferingTiming.rebaseReadyByTimeForOffering("2026-10-19T21:15:00+05:30", offeringDate);
        assertEquals("2026-10-19T21:15", rebased);
    }
}
