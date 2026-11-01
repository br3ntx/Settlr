package com.settlr.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * One "settle up" between two members. fromUser pays the NET amount to toUser, which
 * clears every open share between them in both directions at once (master doc 3.6,
 * extended). It is linked to one Payment per share, so confirming it settles them all.
 * Like a payment it starts PENDING and only counts once toUser confirms.
 */
public class Settlement {

    private static final int MAX_NOTE_LENGTH = 255;

    private int id;                     // 0 = not saved yet
    private int householdId;
    private int fromUser;               // pays the net amount
    private int toUser;                 // receives it and confirms
    private BigDecimal amount;          // net amount, scale 2; 0 when the debts just cancel out
    private String note;                // optional, e.g. "GCash ref 1234"
    private LocalDateTime createdAt;
    private PaymentStatus status;
    private LocalDateTime respondedAt;  // null until confirmed or rejected

    public Settlement(int id, int householdId, int fromUser, int toUser, BigDecimal amount,
                      String note, LocalDateTime createdAt, PaymentStatus status,
                      LocalDateTime respondedAt) {
        setId(id);
        setHouseholdId(householdId);
        setFromUser(fromUser);
        setToUser(toUser);
        setAmount(amount);
        setNote(note);
        setCreatedAt(createdAt);
        setStatus(status);
        setRespondedAt(respondedAt);
    }

    public int getId() { return id; }

    public void setId(int id) {
        if (id < 0) throw new IllegalArgumentException("Settlement id cannot be negative.");
        this.id = id;
    }

    public int getHouseholdId() { return householdId; }

    public void setHouseholdId(int householdId) {
        if (householdId <= 0) throw new IllegalArgumentException("A settlement must belong to a household.");
        this.householdId = householdId;
    }

    public int getFromUser() { return fromUser; }

    public void setFromUser(int fromUser) {
        if (fromUser <= 0) throw new IllegalArgumentException("A settlement needs a payer.");
        if (fromUser == toUser) throw new IllegalArgumentException("You cannot settle up with yourself.");
        this.fromUser = fromUser;
    }

    public int getToUser() { return toUser; }

    public void setToUser(int toUser) {
        if (toUser <= 0) throw new IllegalArgumentException("A settlement needs a receiver.");
        if (toUser == fromUser) throw new IllegalArgumentException("You cannot settle up with yourself.");
        this.toUser = toUser;
    }

    public BigDecimal getAmount() { return amount; }

    public void setAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0)
            throw new IllegalArgumentException("A settle up amount cannot be negative.");
        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public String getNote() { return note; }

    public void setNote(String note) {
        boolean blank = note == null || note.isBlank();
        if (!blank && note.trim().length() > MAX_NOTE_LENGTH)
            throw new IllegalArgumentException("The note can be at most 255 characters.");
        this.note = blank ? null : note.trim();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) {
        if (createdAt == null) throw new IllegalArgumentException("Created time is required.");
        this.createdAt = createdAt;
    }

    public PaymentStatus getStatus() { return status; }

    public void setStatus(PaymentStatus status) {
        if (status == null) throw new IllegalArgumentException("Status is required.");
        this.status = status;
    }

    public LocalDateTime getRespondedAt() { return respondedAt; }

    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public boolean isPending() { return status == PaymentStatus.PENDING; }
}
