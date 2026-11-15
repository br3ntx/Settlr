package com.settlr.model;

/**
 * The raw facts XP is worked out from, as the database counts them: task points finished
 * on time, task points finished late, and payments made on time. Read-only.
 * How much XP each one is worth is a rule of the app, so it lives in XpRules, not here.
 */
public final class XpActivity {

    private final int onTimePoints;
    private final int latePoints;
    private final int onTimePayments;

    /** Takes the three counts; none can be negative. */
    public XpActivity(int onTimePoints, int latePoints, int onTimePayments) {
        if (onTimePoints < 0 || latePoints < 0 || onTimePayments < 0) {
            throw new IllegalArgumentException("XP counts cannot be negative.");
        }
        this.onTimePoints = onTimePoints;
        this.latePoints = latePoints;
        this.onTimePayments = onTimePayments;
    }

    /** Points of tasks marked DONE (on time). */
    public int getOnTimePoints() { return onTimePoints; }

    /** Points of tasks marked DONE_LATE. */
    public int getLatePoints() { return latePoints; }

    /** Confirmed payments that were sent by the day after the expense was due. */
    public int getOnTimePayments() { return onTimePayments; }
}
