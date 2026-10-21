package com.settlr.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The net amount between the current user and one other member (master doc 3.6).
 * Positive = they owe me, negative = I owe them. Computed by BalanceService, never stored.
 */
public class Balance {

    private User otherUser;
    private BigDecimal amount; // always scale 2

    public Balance(User otherUser, BigDecimal amount) {
        setOtherUser(otherUser);
        setAmount(amount);
    }

    public User getOtherUser() { return otherUser; }

    public void setOtherUser(User otherUser) {
        if (otherUser == null) throw new IllegalArgumentException("A balance needs another member.");
        this.otherUser = otherUser;
    }

    public BigDecimal getAmount() { return amount; }

    public void setAmount(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount is required.");
        if (amount.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Amount can have at most 2 decimal places.");
        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY); // exact, never rounds
    }

    /** True when they owe me money. */
    public boolean theyOweMe() { return amount.signum() > 0; }

    /** True when I owe them money. */
    public boolean iOweThem() { return amount.signum() < 0; }

    /** True when nobody owes anything. */
    public boolean isZero() { return amount.signum() == 0; }

    /** The amount without its sign, for text like "You owe Ana P250.00". */
    public BigDecimal getAbsoluteAmount() { return amount.abs(); }
}