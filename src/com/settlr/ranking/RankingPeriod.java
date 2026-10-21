package com.settlr.ranking;

import com.settlr.util.DateTimeUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The three tabs of the leaderboard and the date range each one covers.
 *
 * <p>Why an enum: the periods are a fixed set, and each one knows its own start date,
 * so no other class needs an if/else on "week or month".
 */
public enum RankingPeriod {

    WEEK("This Week"),
    MONTH("This Month"),
    ALL_TIME("All Time");

    /** Master doc: All Time starts in year 2000, long before any Settlr data exists. */
    private static final LocalDateTime ALL_TIME_START = LocalDateTime.of(2000, 1, 1, 0, 0);

    /** Far-future bound for the Java rankings that need an upper limit (MySQL DATETIME goes to 9999). */
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 0, 0);

    private final String label;

    RankingPeriod(String label) {
        this.label = label;
    }

    /** Text for the tab button. */
    public String getLabel() {
        return label;
    }

    /** WEEK = Monday 00:00, MONTH = the 1st 00:00, ALL_TIME = year 2000. Local (Philippine) time. */
    public LocalDateTime getStart(LocalDate today) {
        return switch (this) {
            case WEEK -> DateTimeUtil.weekStart(today);
            case MONTH -> DateTimeUtil.monthStart(today);
            case ALL_TIME -> ALL_TIME_START;
        };
    }

    /**
     * A date that falls inside the PREVIOUS period (a week or a month earlier). Pass it to
     * getStart()/getEnd() to get last week's or last month's edges. Each constant knows its own
     * step, so MostImproved needs no if/else on the period. All Time has no "before".
     */
    public LocalDate dayInPreviousPeriod(LocalDate today) {
        return switch (this) {
            case WEEK -> today.minusWeeks(1);
            case MONTH -> today.minusMonths(1);
            case ALL_TIME -> throw new IllegalStateException("All Time has no previous period.");
        };
    }

    /**
     * Where this period ends (exclusive): the start of the next week/month.
     * Only SettlrScore and MostImproved use it, because they compare two windows
     * (this week vs last week) and so need both edges. Nothing is dated in the future, so
     * "end of week" gives the same answer as "now".
     */
    public LocalDateTime getEnd(LocalDate today) {
        return switch (this) {
            case WEEK -> getStart(today).plusWeeks(1);
            case MONTH -> getStart(today).plusMonths(1);
            case ALL_TIME -> FAR_FUTURE;
        };
    }
}
