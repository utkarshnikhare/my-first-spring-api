package com.example.my_first_spring_api;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * The single business time zone of SocioMart.
 *
 * <p>Order cut-offs, ready-by times, "today", recurring occurrences and
 * reminders are all evaluated with {@code LocalDate/LocalDateTime/LocalTime.now()}
 * which read the JVM default zone. Hosting platforms run containers in UTC, so
 * the default zone is pinned once, at startup, instead of at every call site.
 * The Dockerfile also sets {@code TZ} so the OS and the JVM agree.</p>
 */
public final class AppTimeZone {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private AppTimeZone() {
    }

    /** Pins the JVM default time zone to {@link #ZONE}. Safe to call repeatedly. */
    public static void apply() {
        if (!ZONE.equals(TimeZone.getDefault().toZoneId())) {
            TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
        }
    }
}
