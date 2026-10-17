package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.model.Product;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared authoritative rules for offering dates and order windows. */
final class OfferingTiming {
    private static final Pattern LEGACY_READY_TIME = Pattern.compile(
            "^(\\d{1,2}):(\\d{2})\\s*(AM|PM)?\\s*(today|tomorrow|tmr)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WEEKDAY = Pattern.compile(
            "(monday|tuesday|wednesday|thursday|friday|saturday|sunday)",
            Pattern.CASE_INSENSITIVE);

    private OfferingTiming() {}

    static String normalizeHhmm(String value, String fieldLabel) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new IllegalArgumentException(fieldLabel + " must use 24-hour HH:mm format, e.g. 08:30");
        }
        return normalized;
    }

    static String requireOrdersClose(String value) {
        String close = normalizeHhmm(value, "Orders Close");
        if (close == null) throw new IllegalArgumentException("Orders Close is required.");
        return close;
    }

    static String resolveOrdersClose(Product product) {
        String close = normalizeHhmm(product.getOrderWindowEnd(), "Orders Close");
        return close != null ? close : normalizeHhmm(product.getCutoffTime(), "Cutoff");
    }

    static void validateNewOffering(LocalDate offeringDate, boolean preorder,
                                    String orderWindowStart, String orderWindowEnd,
                                    String readyByTime) {
        validateNewOffering(offeringDate, preorder, orderWindowStart, orderWindowEnd,
                readyByTime, LocalDate.now(AppTimeZone.ZONE), LocalTime.now(AppTimeZone.ZONE));
    }

    static void validateNewOffering(LocalDate offeringDate, boolean preorder,
                                    String orderWindowStart, String orderWindowEnd,
                                    String readyByTime, LocalDate today, LocalTime now) {
        if (offeringDate == null) throw new IllegalArgumentException("Offering For is required.");
        if (offeringDate.isBefore(today)) {
            throw new IllegalArgumentException("Offering date cannot be in the past.");
        }
        if (preorder && !offeringDate.isAfter(today)) {
            throw new IllegalArgumentException("Pre-orders must be for a future offering date.");
        }
        String open = normalizeHhmm(orderWindowStart, "Orders Open");
        String close = requireOrdersClose(orderWindowEnd);
        validateWindowPair(open, close);
        LocalDateTime delivery = parseReadyByTime(readyByTime, today);
        if (delivery.toLocalDate().isBefore(offeringDate)) {
            throw new IllegalArgumentException("Delivery / Ready By cannot be before the offering date.");
        }
        LocalDate orderingDate = preorder ? offeringDate.minusDays(1) : offeringDate;
        // 2-minute tolerance buffer so clock ticks right at submission don't cause rejection
        int nowMinutes = now.toSecondOfDay() / 60;
        if (!orderingDate.isAfter(today) && minutes(close) < nowMinutes - 2) {
            throw new IllegalArgumentException("Orders Close must not already have passed for this offering date.");
        }
        LocalDateTime closeDateTime = java.time.LocalDateTime.of(orderingDate, LocalTime.of(minutes(close) / 60, minutes(close) % 60));
        if (closeDateTime.isAfter(delivery)) {
            throw new IllegalArgumentException("Orders Close must be before Delivery / Ready By.");
        }
        if (!offeringDate.isAfter(today) && delivery.isBefore(LocalDateTime.of(today, now).minusMinutes(2))) {
            throw new IllegalArgumentException("Delivery / Ready By must not already have passed.");
        }
    }

    static void enforceOrderWindow(Product product, LocalDate orderingDate, String context) {
        enforceOrderWindow(product, orderingDate, context, LocalDate.now(AppTimeZone.ZONE), LocalTime.now(AppTimeZone.ZONE));
    }

    static void enforceOrderWindow(Product product, LocalDate orderingDate, String context,
                                   LocalDate today, LocalTime now) {
        if (orderingDate.isBefore(today)) throw closed(product, context);
        if (orderingDate.isAfter(today)) {
            // A future pre-order is open for ordering now; evaluate its Orders Open
            // time now, but defer Orders Close until the offering's ordering date.
            String open = normalizeHhmm(product.getOrderWindowStart(), "Orders Open");
            if (open != null && now.toSecondOfDay() / 60 < minutes(open)) {
                throw new IllegalArgumentException("Orders for '" + product.getName() + "' have not opened yet.");
            }
            return;
        }
        String open = normalizeHhmm(product.getOrderWindowStart(), "Orders Open");
        if (open != null && now.toSecondOfDay() / 60 < minutes(open)) {
            throw new IllegalArgumentException("Orders for '" + product.getName() + "' have not opened yet.");
        }
        String close = resolveOrdersClose(product);
        // Products created before Orders Close was introduced remain orderable
        // for compatibility. New create/update paths always persist a close.
        if (close == null) return;
        if (now.toSecondOfDay() / 60 > minutes(close)) throw closed(product, context);
    }
    static boolean isWindowOpenNow(Product product) {
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        return isWindowOpenNow(product, today, LocalTime.now(AppTimeZone.ZONE));
    }

    static boolean isWindowOpenNow(Product product, LocalDate today, LocalTime now) {
        LocalDate offeringDate = product.getAvailableDate() != null
                ? product.getAvailableDate() : today;
        LocalDate orderingDate = Boolean.TRUE.equals(product.getIsPreorder())
                ? offeringDate.minusDays(1) : offeringDate;
        try {
            enforceOrderWindow(product, orderingDate, "today", today, now);
            return !product.isSoldOut() && !product.isOrdersPaused();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Query-time lifecycle state used consistently by Buyer and Seller DTOs. */
    static String lifecycleState(Product product, LocalDate today, LocalTime now) {
        if (product.isOrdersPaused()) return "PAUSED";
        if (product.isSoldOut()) return "SOLD_OUT";
        LocalDate offeringDate = product.getAvailableDate() != null
                ? product.getAvailableDate() : today;
        if (offeringDate.isBefore(today)) return "HISTORY";
        LocalDate orderingDate = Boolean.TRUE.equals(product.getIsPreorder())
                ? offeringDate.minusDays(1) : offeringDate;
        if (!isWindowOpenNow(product, today, now)) return "ORDERS_CLOSED";
        return Boolean.TRUE.equals(product.getIsPreorder()) ? "PRE_ORDER" : "LIVE";
    }

    static void validateWindowPair(String orderWindowStart, String orderWindowEnd) {
        String open = normalizeHhmm(orderWindowStart, "Orders Open");
        String close = requireOrdersClose(orderWindowEnd);
        if (open != null && minutes(open) >= minutes(close)) {
            throw new IllegalArgumentException("Orders Open must be before Orders Close.");
        }
    }

    /**
     * V1 live-offering rule (Requirement 5): once an offering has orders, the
     * Orders Close cutoff may only be EXTENDED, never shortened. Extending keeps
     * every existing customer order intact while still letting the seller accept
     * more orders. Comparison is time-of-day based (HH:mm) and null-safe.
     *
     * @return true when {@code candidate} is strictly later than {@code current}
     */
    static boolean isLaterHhmm(String candidate, String current) {
        String a = normalizeHhmm(candidate, "Orders Close");
        String b = normalizeHhmm(current, "Orders Close");
        if (a == null || b == null) return false;
        return minutes(a) > minutes(b);
    }

    /**
     * A saved template can contain a ready time saved for its original date.
     * When it is republished for a later date, retain the selected time-of-day
     * but move the delivery date forward so the template remains valid.
     */
    static String rebaseReadyByTime(String value, LocalDate offeringDate) {
        LocalDateTime parsed = parseReadyByTime(value);
        if (parsed.toLocalDate().isBefore(offeringDate)) {
            return LocalDateTime.of(offeringDate, parsed.toLocalTime()).toString();
        }
        return value.trim();
    }

    /**
     * Flexible day-boundary tolerance used by the create/update paths before
     * {@link #validateNewOffering}. UI and e2e clients build the Delivery /
     * Ready By timestamp from their own clock, so near a day boundary (late
     * night / early morning) the date component can land before the offering
     * date it was meant for. Correct the date instead of rejecting an
     * otherwise-valid offering: keep the seller-selected time-of-day and move
     * it onto the offering date. The forward correction is unbounded in days
     * so any multi-day client/server skew is healed the same way.
     *
     * <p>Explicit day words ({@code today}, {@code tomorrow}, a weekday) are
     * deliberate human intent rather than clock skew, so they are returned
     * untouched and remain strictly enforced by {@link #validateNewOffering}.
     *
     * <p>Values that already resolve to the offering date or later (the e2e
     * "ready-by tomorrow, offering today" case), and values that fail to
     * parse, are returned unchanged so validation reports them exactly as
     * before.
     */
    static String rebaseReadyByTimeForOffering(String value, LocalDate offeringDate) {
        if (value == null || value.isBlank() || offeringDate == null) return value;
        String text = value.trim();
        if (WEEKDAY.matcher(text).find()) return value;
        Matcher legacy = LEGACY_READY_TIME.matcher(text);
        // A stored "today"/"tomorrow" keyword expresses a chosen day; keep it
        // so validateNewOffering can reject a day that precedes the offering.
        if (legacy.matches() && legacy.group(4) != null) return value;
        LocalDateTime resolved;
        boolean hasOffsetOrZ = false;
        try {
            resolved = LocalDateTime.parse(text);
        } catch (RuntimeException notIso) {
            try {
                resolved = OffsetDateTime.parse(text).atZoneSameInstant(AppTimeZone.ZONE).toLocalDateTime();
                hasOffsetOrZ = true;
            } catch (RuntimeException notOffset) {
                try {
                    resolved = ZonedDateTime.parse(text).withZoneSameInstant(AppTimeZone.ZONE).toLocalDateTime();
                    hasOffsetOrZ = true;
                } catch (RuntimeException notZoned) {
                    try {
                        resolved = Instant.parse(text).atZone(AppTimeZone.ZONE).toLocalDateTime();
                        hasOffsetOrZ = true;
                    } catch (RuntimeException notInstant) {
                        if (!legacy.matches()) return value; // Malformed input: validate reports it.
                        try {
                            resolved = parseReadyByTime(text, LocalDate.now(AppTimeZone.ZONE));
                        } catch (RuntimeException invalid) {
                            return value;
                        }
                    }
                }
            }
        }
        if (resolved.toLocalDate().isBefore(offeringDate)) {
            return LocalDateTime.of(offeringDate, resolved.toLocalTime()).toString();
        }
        if (hasOffsetOrZ) {
            return resolved.toString();
        }
        return value;
    }

    static LocalDateTime parseReadyByTime(String value) {
        return parseReadyByTime(value, LocalDate.now(AppTimeZone.ZONE));
    }

    static LocalDateTime parseReadyByTime(String value, LocalDate today) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Delivery / Ready By is required.");
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException ignored) {
            // Continue with accepted formats below.
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(AppTimeZone.ZONE).toLocalDateTime();
        } catch (RuntimeException ignored) {
        }
        try {
            return ZonedDateTime.parse(text).withZoneSameInstant(AppTimeZone.ZONE).toLocalDateTime();
        } catch (RuntimeException ignored) {
        }
        try {
            return Instant.parse(text).atZone(AppTimeZone.ZONE).toLocalDateTime();
        } catch (RuntimeException ignored) {
        }
        if (text.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            return LocalDateTime.of(today, LocalTime.parse(text));
        }
        Matcher matcher = LEGACY_READY_TIME.matcher(text);
        if (matcher.matches()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = Integer.parseInt(matcher.group(2));
            String ampm = matcher.group(3);
            if (ampm != null) {
                if (hour < 1 || hour > 12) throw invalidReadyTime();
                hour = hour % 12 + (ampm.equalsIgnoreCase("PM") ? 12 : 0);
            } else if (hour > 23) {
                throw invalidReadyTime();
            }
            LocalTime time;
            try {
                time = LocalTime.of(hour, minute);
            } catch (RuntimeException ex) {
                throw invalidReadyTime();
            }
            String day = matcher.group(4);
            LocalDate date = day == null || day.equalsIgnoreCase("today")
                    ? today : today.plusDays(1);
            return LocalDateTime.of(date, time);
        }
        Matcher weekday = WEEKDAY.matcher(text);
        if (weekday.find()) {
            Matcher timeMatcher = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s*(AM|PM)?", Pattern.CASE_INSENSITIVE)
                    .matcher(text);
            if (timeMatcher.find()) {
                int hour = Integer.parseInt(timeMatcher.group(1));
                int minute = Integer.parseInt(timeMatcher.group(2));
                String ampm = timeMatcher.group(3);
                if (ampm != null && hour >= 1 && hour <= 12) {
                    hour = hour % 12 + (ampm.equalsIgnoreCase("PM") ? 12 : 0);
                }
                LocalDate current = today;
                DayOfWeek target = DayOfWeek.valueOf(weekday.group(1).toUpperCase(Locale.ROOT));
                int days = (target.getValue() - current.getDayOfWeek().getValue() + 7) % 7;
                if (days == 0) days = 7;
                return LocalDateTime.of(current.plusDays(days), LocalTime.of(hour, minute));
            }
        }
        throw new IllegalArgumentException("Delivery / Ready By must be a valid date/time, e.g. 2026-09-24T13:00.");
    }

    private static IllegalArgumentException closed(Product product, String context) {
        return new IllegalArgumentException("The order cutoff for '" + product.getName()
                + "' has passed — orders " + context + " are closed.");
    }

    private static IllegalArgumentException invalidReadyTime() {
        return new IllegalArgumentException("Delivery / Ready By must be a valid date/time, e.g. 1:00 PM today.");
    }

    private static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }
}
