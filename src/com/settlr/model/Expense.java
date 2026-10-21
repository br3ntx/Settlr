package com.settlr.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money one member paid for the group (for example a P900 grocery run).
 * It stores the whole bill; each person's portion is an ExpenseShare.
 * Mirrors the expenses table. Saving it is the DAO's job, not this class's.
 */
public class Expense {

    public static final int MAX_TITLE_LENGTH = 80;

    /** Biggest value a DECIMAL(10,2) column can hold (8 digits before the point, 2 after). */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private int id;                 // 0 = not saved yet
    private int householdId;
    private Integer billId;         // null unless created from a recurring bill
    private String title;
    private ExpenseCategory category;
    private BigDecimal amount;      // always scale 2, for example 900.00
    private int paidBy;
    private LocalDate dueDate;
    private int createdBy;
    private LocalDateTime createdAt;

    public Expense(int id, int householdId, Integer billId, String title,
                   ExpenseCategory category, BigDecimal amount, int paidBy,
                   LocalDate dueDate, int createdBy, LocalDateTime createdAt) {
        setId(id);
        setHouseholdId(householdId);
        setBillId(billId);
        setTitle(title);
        setCategory(category);
        setAmount(amount);
        setPaidBy(paidBy);
        setDueDate(dueDate);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
    }

    public int getId() { return id; }

    /** 0 means "not saved yet"; the database assigns the real id later. */
    public void setId(int id) {
        if (id < 0) throw new IllegalArgumentException("Expense id cannot be negative.");
        this.id = id;
    }

    public int getHouseholdId() { return householdId; }

    public void setHouseholdId(int householdId) {
        if (householdId <= 0) throw new IllegalArgumentException("An expense must belong to a household.");
        this.householdId = householdId;
    }

    public Integer getBillId() { return billId; }

    /** Null is allowed: most expenses are typed in by hand and have no recurring bill. */
    public void setBillId(Integer billId) {
        if (billId != null && billId <= 0)
            throw new IllegalArgumentException("Bill id must be positive when it is given.");
        this.billId = billId;
    }

    public String getTitle() { return title; }

    public void setTitle(String title) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Please enter an expense title.");
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH)
            throw new IllegalArgumentException("Expense title can be at most 80 characters.");
        this.title = trimmed;
    }

    public ExpenseCategory getCategory() { return category; }

    public void setCategory(ExpenseCategory category) {
        if (category == null) throw new IllegalArgumentException("Please choose a category.");
        this.category = category;
    }

    public BigDecimal getAmount() { return amount; }

    public void setAmount(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount is required.");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
        this.amount = checkedMoney(amount);
    }

    public int getPaidBy() { return paidBy; }

    public void setPaidBy(int paidBy) {
        if (paidBy <= 0) throw new IllegalArgumentException("Please choose who paid.");
        this.paidBy = paidBy;
    }

    public LocalDate getDueDate() { return dueDate; }

    public void setDueDate(LocalDate dueDate) {
        if (dueDate == null) throw new IllegalArgumentException("Please choose a due date.");
        this.dueDate = dueDate;
    }

    public int getCreatedBy() { return createdBy; }

    public void setCreatedBy(int createdBy) {
        if (createdBy <= 0) throw new IllegalArgumentException("An expense needs a creator.");
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) {
        if (createdAt == null) throw new IllegalArgumentException("Created time is required.");
        this.createdAt = createdAt;
    }

    /**
     * Rejects more than 2 decimals instead of rounding them away (silent rounding
     * would change the user's money), and rejects what DECIMAL(10,2) cannot store.
     * stripTrailingZeros lets 12.500 through because it really is 12.5.
     */
    private static BigDecimal checkedMoney(BigDecimal value) {
        if (value.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Amount can have at most 2 decimal places.");
        BigDecimal exact = value.setScale(2, RoundingMode.UNNECESSARY); // exact, never rounds
        if (exact.compareTo(MAX_AMOUNT) > 0)
            throw new IllegalArgumentException("Amount is too large.");
        return exact;
    }
}