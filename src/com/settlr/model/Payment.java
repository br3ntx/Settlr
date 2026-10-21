package com.settlr.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * A record that someone paid toward an ExpenseShare. Mirrors the payments table.
 * It only holds data: confirming or rejecting a payment is PaymentService's job.
 */
public class Payment {

    public static final int MAX_NOTE_LENGTH = 255;

    /** Biggest value a DECIMAL(10,2) column can hold. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private int id;                     // 0 = not saved yet
    private int shareId;
    private int fromUser;               // the debtor who pays
    private int toUser;                 // the receiver who confirms
    private BigDecimal amount;          // always scale 2
    private LocalDateTime paidAt;
    private PaymentStatus status;
    private LocalDateTime confirmedAt;  // null until confirmed
    private String note;                // optional, may be null

    public Payment(int id, int shareId, int fromUser, int toUser, BigDecimal amount,
                   LocalDateTime paidAt, PaymentStatus status,
                   LocalDateTime confirmedAt, String note) {
        setId(id);
        setShareId(shareId);
        setFromUser(fromUser);
        setToUser(toUser);
        setAmount(amount);
        setPaidAt(paidAt);
        setStatus(status);
        setConfirmedAt(confirmedAt);
        setNote(note);
    }

    public int getId() { return id; }

    /** 0 means "not saved yet". */
    public void setId(int id) {
        if (id < 0) throw new IllegalArgumentException("Payment id cannot be negative.");
        this.id = id;
    }

    public int getShareId() { return shareId; }

    public void setShareId(int shareId) {
        if (shareId <= 0) throw new IllegalArgumentException("A payment must be for a share.");
        this.shareId = shareId;
    }

    public int getFromUser() { return fromUser; }

    public void setFromUser(int fromUser) {
        if (fromUser <= 0) throw new IllegalArgumentException("A payment needs a payer.");
        requireDifferent(fromUser, toUser);
        this.fromUser = fromUser;
    }

    public int getToUser() { return toUser; }

    public void setToUser(int toUser) {
        if (toUser <= 0) throw new IllegalArgumentException("A payment needs a receiver.");
        requireDifferent(fromUser, toUser);
        this.toUser = toUser;
    }

    public BigDecimal getAmount() { return amount; }

    public void setAmount(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount is required.");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
        if (amount.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Amount can have at most 2 decimal places.");
        BigDecimal exact = amount.setScale(2, RoundingMode.UNNECESSARY); // exact, never rounds
        if (exact.compareTo(MAX_AMOUNT) > 0)
            throw new IllegalArgumentException("Amount is too large.");
        this.amount = exact;
    }

    public LocalDateTime getPaidAt() { return paidAt; }

    public void setPaidAt(LocalDateTime paidAt) {
        if (paidAt == null) throw new IllegalArgumentException("Payment time is required.");
        this.paidAt = paidAt;
    }

    public PaymentStatus getStatus() { return status; }

    public void setStatus(PaymentStatus status) {
        if (status == null) throw new IllegalArgumentException("Status is required.");
        this.status = status;
    }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }

    /** Null is allowed: it stays null until the receiver confirms. */
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public String getNote() { return note; }

    public void setNote(String note) {
        // Blank means "no note" so the database stores NULL, not "".
        boolean blank = note == null || note.isBlank();
        if (!blank && note.trim().length() > MAX_NOTE_LENGTH)
            throw new IllegalArgumentException("Note can be at most 255 characters.");
        this.note = blank ? null : note.trim();
    }

    /**
     * A person cannot pay themselves. A value of 0 means "not set yet" (the
     * constructor sets one side first), so the check only runs when both are set.
     */
    private static void requireDifferent(int fromUser, int toUser) {
        if (fromUser > 0 && toUser > 0 && fromUser == toUser)
            throw new IllegalArgumentException("You cannot pay yourself.");
    }
}