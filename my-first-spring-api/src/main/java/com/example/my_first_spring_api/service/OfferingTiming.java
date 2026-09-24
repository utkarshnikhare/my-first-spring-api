package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Product;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
                readyByTime, LocalDate.now(), LocalTime.now());
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
        if (!orderingDate.isAfter(today) && minutes(close) < now.toSecondOfDay() / 60) {
            throw new IllegalArgumentException("Orders Close must not already have passed for this offering date.");
        }
        if (java.time.LocalDateTime.of(orderingDate, LocalTime.of(minutes(close) / 60, minutes(close) % 60))
                .isAfter(delivery)) {
            throw new IllegalArgumentException("Orders Close must be before Delivery / Ready By.");
        }
    }

    static void enforceOrderWindow(Product product, LocalDate orderingDate, String context) {
        enforceOrderWindow(product, orderingDate, context, LocalDate.now(), LocalTime.now());
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
        LocalDate today = LocalDate.now();
        return isWindowOpenNow(product, today, LocalTime.now());
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

    static void validateWindowPair(String orderWindowStart, String orderWindowEnd) {
        String open = normalizeHhmm(orderWindowStart, "Orders Open");
        String close = requireOrdersClose(orderWindowEnd);
        if (open != null && minutes(open) >= minutes(close)) {
            throw new IllegalArgumentException("Orders Open must be before Orders Close.");
        }
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

    static LocalDateTime parseReadyByTime(String value) {
        return parseReadyByTime(value, LocalDate.now());
    }

    static LocalDateTime parseReadyByTime(String value, LocalDate today) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Delivery / Ready By is required.");
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException ignored) {
            // Continue with the accepted legacy text format below.
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
