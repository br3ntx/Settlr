package com.settlr.split;

import com.settlr.model.ExpenseShare;

import java.math.BigDecimal;
import java.util.List;

/**
 * A rule for dividing an expense among members. Today only EqualSplit exists;
 * a future PercentSplit can implement this same interface without changing
 * the code that calls it (polymorphism).
 */
public interface SplitStrategy {

    /**
     * Works out each member's share of {@code amount}.
     *
     * @param amount    the whole expense
     * @param payerId   the member who paid
     * @param memberIds the members to split among, in the order shares should come back
     * @return one ExpenseShare per member (not saved yet: id and expenseId are 0)
     */
    List<ExpenseShare> split(BigDecimal amount, int payerId, List<Integer> memberIds);
}