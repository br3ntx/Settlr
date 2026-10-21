package com.settlr.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a recurring household bill and the members who share it.
 */
public class RecurringBill {

    private int id;
    private int householdId;
    private String title;
    private ExpenseCategory category;
    private BigDecimal amount;
    private int paidBy;
    private int dueDay;
    private boolean active;
    private LocalDate lastGenerated;
    private int createdBy;
    private LocalDateTime createdAt;
    private List<Integer> memberIds;

    public RecurringBill(
            int id,
            int householdId,
            String title,
            ExpenseCategory category,
            BigDecimal amount,
            int paidBy,
            int dueDay,
            boolean active,
            LocalDate lastGenerated,
            int createdBy,
            LocalDateTime createdAt,
            List<Integer> memberIds) {

        setId(id);
        setHouseholdId(householdId);
        setTitle(title);
        setCategory(category);
        setAmount(amount);
        setPaidBy(paidBy);
        setDueDay(dueDay);
        setActive(active);
        setLastGenerated(lastGenerated);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setMemberIds(memberIds);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID cannot be negative.");
        }
        this.id = id;
    }

    public int getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(int householdId) {
        if (householdId <= 0) {
            throw new IllegalArgumentException("Household ID must be positive.");
        }
        this.householdId = householdId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank.");
        }
        this.title = title.trim();
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public void setCategory(ExpenseCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null.");
        }
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Amount cannot have more than two decimal places.");
        }
        this.amount = amount;
    }

    public int getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(int paidBy) {
        if (paidBy <= 0) {
            throw new IllegalArgumentException("Payer ID must be positive.");
        }
        this.paidBy = paidBy;
    }

    public int getDueDay() {
        return dueDay;
    }

    public void setDueDay(int dueDay) {
        if (dueDay < 1 || dueDay > 31) {
            throw new IllegalArgumentException(
                    "Due day must be between 1 and 31.");
        }
        this.dueDay = dueDay;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getLastGenerated() {
        return lastGenerated;
    }

    public void setLastGenerated(LocalDate lastGenerated) {
        this.lastGenerated = lastGenerated;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        if (createdBy <= 0) {
            throw new IllegalArgumentException("Creator ID must be positive.");
        }
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Integer> getMemberIds() {
        return new ArrayList<>(memberIds);
    }

    public void setMemberIds(List<Integer> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one member is required.");
        }

        if (memberIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException(
                    "Member IDs must be positive.");
        }

        this.memberIds = new ArrayList<>(memberIds);
    }

    /**
     * Creates an expense for the specified month.
     *
     * @param month the month for which to generate the expense
     * @return a new expense based on this recurring bill
     */
    public Expense createExpenseFor(YearMonth month) {
        if (month == null) {
            throw new IllegalArgumentException("Month cannot be null.");
        }

        LocalDate dueDate = month.atDay(
                Math.min(dueDay, month.lengthOfMonth()));

        return new Expense(
                0,
                householdId,
                id,
                title,
                category,
                amount,
                paidBy,
                dueDate,
                createdBy,
                null
        );
    }
}
