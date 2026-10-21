package com.settlr.util;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Date and time helpers, so every screen shows dates the same way and every
 * ranking uses the same week and month boundaries.
 * Settlr weeks start on Monday 00:00 (master doc section 4).
 */
public final class DateTimeUtil {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private DateTimeUtil() { }

    /** 2026-10-20T15:00 -> "Oct 20, 3:00 PM". */
    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "" : DATE_TIME.format(dateTime);
    }

    /** 2026-10-20 -> "Oct 20, 2026". */
    public static String format(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    /**
     * Short relative time for feeds and lists:
     * "just now", "5m ago", "2h ago", "3d ago", or the date if over a week ago.
     * Future times read "in 5m", "in 2h", "in 3d".
     */
    public static String timeAgo(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        LocalDateTime now = LocalDateTime.now();
        Duration d = Duration.between(dateTime, now);
        boolean future = d.isNegative();
        long minutes = Math.abs(d.toMinutes());

        String amount;
        if (minutes < 1) {
            return "just now";
        } else if (minutes < 60) {
            amount = minutes + "m";
        } else if (minutes < 60 * 24) {
            amount = (minutes / 60) + "h";
        } else if (minutes < 60 * 24 * 7) {
            amount = (minutes / (60 * 24)) + "d";
        } else {
            return format(dateTime.toLocalDate());
        }
        return future ? "in " + amount : amount + " ago";
    }

    /** Monday 00:00 of the week containing this day. */
    public static LocalDateTime weekStart(LocalDate anyDay) {
        return anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
    }

    /** The 1st of this day's month at 00:00. */
    public static LocalDateTime monthStart(LocalDate anyDay) {
        return anyDay.withDayOfMonth(1).atStartOfDay();
    }
}
