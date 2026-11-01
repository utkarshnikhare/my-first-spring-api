package com.example.my_first_spring_api;

import java.time.ZoneId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.TimeZone;

/**
 * The single business time zone of SocioMart.
 *
 * <p>Order cut-offs, ready-by times, "today", recurring occurrences and
 * reminders use this zone explicitly. Hosting platforms run containers in UTC,
 * so the business clock must not depend on the JVM default zone. The Dockerfile
 * also sets {@code TZ} so the OS and the JVM agree.</p>
 */
public final class AppTimeZone {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static volatile Clock testClock;

    private AppTimeZone() {
    }

    /** Pins the JVM default time zone to {@link #ZONE}. Safe to call repeatedly. */
    public static void apply() {
        if (!ZONE.equals(TimeZone.getDefault().toZoneId())) {
            TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
        }
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(currentClock());
    }

    public static LocalDate today() {
        return LocalDate.now(currentClock());
    }

    public static LocalTime time() {
        return LocalTime.now(currentClock());
    }

    static void setTestTime(LocalDateTime now) {
        testClock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
    }

    static void resetTestTime() {
        testClock = null;
    }

    private static Clock currentClock() {
        Clock clock = testClock;
        return clock != null ? clock : Clock.system(ZONE);
    }
}
