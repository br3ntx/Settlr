package com.settlr.service;

import com.settlr.exception.SettlrException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A payment someone says they sent you, waiting for you to confirm it or say you didn't get it.
 *
 * There are two kinds behind this: a settle up (Settlement, which clears everything between two
 * people at once) and an older single-share Payment. They're different classes with different
 * methods, so each one gets an adapter that makes it look like a PendingPayment
 * (Adapter pattern): SettleUpAdapter and SinglePaymentAdapter. The screens then show one list
 * with one kind of row, and Confirm / Not received work the same for both.
 */
public interface PendingPayment {

    /** One row of the Confirm popup's breakdown: an expense and how much of the payment is for it. */
    final class Line {
        private final String label;
        private final BigDecimal amount;

        public Line(String label, BigDecimal amount) {
            this.label = label;
            this.amount = amount;
        }

        /** e.g. "Rent", or "Water bill (you owed Dani)" for a share that cancels out. */
        public String getLabel() { return label; }
        /** Negative for a share of yours that cancels out part of theirs. */
        public BigDecimal getAmount() { return amount; }
    }

    /** Who sent it, e.g. "Dani". */
    String getFromName();

    BigDecimal getAmount();

    /** When they said they sent it. */
    LocalDateTime getSentAt();

    /** The main line, e.g. "Dani says they paid you P2,400.00". */
    String getHeadline();

    /** The smaller line under it, e.g. "for Rent" or the note they wrote. */
    String getDetails();

    /** The Confirm popup's short title, e.g. "Did Dani pay you?" */
    String getConfirmTitle();

    /** What the payment covers, one Line per expense; they add up to getAmount(). */
    List<Line> getBreakdown();

    /** The question the Confirm popup asks, e.g. "Did Dani pay you P2,400.00?" */
    String getConfirmQuestion();

    /** What they say they sent and what confirming means, for the Confirm popup. */
    String getConfirmExplanation();

    /** The message after confirming. */
    String getConfirmedMessage();

    /** The question the Not received popup asks. */
    String getRejectQuestion();

    /** The message after saying it wasn't received. */
    String getRejectedMessage();

    /** "Yes, I got it." */
    void confirm() throws SettlrException;

    /** "I didn't get it." Nothing is settled, and they can send it again. */
    void reject() throws SettlrException;
}
