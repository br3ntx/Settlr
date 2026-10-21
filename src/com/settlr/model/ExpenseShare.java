package com.settlr.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * One member's portion of an Expense (their slice of the bill).
 * Mirrors the expense_shares table. settledAt stays null until the share is paid.
 */
public class ExpenseShare {

    /** Biggest value a DECIMAL(10,2) column can hold. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private int id;                   // 0 = not saved yet
    private int expenseId;            // 0 = the expense is not saved yet
    private int userId;
    private BigDecimal amountOwed;    // always scale 2
    private LocalDateTime settledAt;  // null = still unpaid

    public ExpenseShare(int id, int expenseId, int userId,
                        BigDecimal amountOwed, LocalDateTime settledAt) {
        setId(id);
        setExpenseId(expenseId);
        setUserId(userId);
        setAmountOwed(amountOwed);
        setSettledAt(settledAt);
    }

    public int getId() { return id; }

    /** 0 means "not saved yet". */
    public void setId(int id) {
        if (id < 0) throw new IllegalArgumentException("Share id cannot be negative.");
        this.id = id;
    }

    public int getExpenseId() { return expenseId; }

    /**
     * 0 is allowed on purpose: a split is calculated before the expense gets its
     * database id, so the service fills the real id in later.
     */
    public void setExpenseId(int expenseId) {
        if (expenseId < 0) throw new IllegalArgumentException("Expense id cannot be negative.");
        this.expenseId = expenseId;
    }

    public int getUserId() { return userId; }

    public void setUserId(int userId) {
        if (userId <= 0) throw new IllegalArgumentException("A share needs a member.");
        this.userId = userId;
    }

    public BigDecimal getAmountOwed() { return amountOwed; }

    /** Zero is allowed: a tiny expense split many ways can leave some members with P0.00. */
    public void setAmountOwed(BigDecimal amountOwed) {
        if (amountOwed == null) throw new IllegalArgumentException("Amount owed is required.");
        if (amountOwed.signum() < 0) throw new IllegalArgumentException("Amount owed cannot be negative.");
        if (amountOwed.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Amount owed can have at most 2 decimal places.");
        BigDecimal exact = amountOwed.setScale(2, RoundingMode.UNNECESSARY); // exact, never rounds
        if (exact.compareTo(MAX_AMOUNT) > 0)
            throw new IllegalArgumentException("Amount owed is too large.");
        this.amountOwed = exact;
    }

    public LocalDateTime getSettledAt() { return settledAt; }

    /** Null is allowed: it means the share is not settled yet. */
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }

    /** True once the share has a settled time. */
    public boolean isSettled() { return settledAt != null; }
}