package com.settlr.split;

import com.settlr.model.ExpenseShare;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Splits an amount equally, rounded down to the centavo (rule 3.5 of the master
 * document). Nothing is saved here; it only calculates the shares.
 */
public class EqualSplit implements SplitStrategy {

    /** Biggest value a DECIMAL(10,2) column can hold. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    @Override
    public List<ExpenseShare> split(BigDecimal amount, int payerId, List<Integer> memberIds) {
        validate(amount, payerId, memberIds);

        BigDecimal total = amount.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal count = BigDecimal.valueOf(memberIds.size());

        // Round every share DOWN so nobody is ever charged more than their fair part...
        BigDecimal baseShare = total.divide(count, 2, RoundingMode.DOWN);
        // ...then the centavos lost by rounding down are exactly what is left over.
        BigDecimal leftover = total.subtract(baseShare.multiply(count));

        int leftoverIndex = findLeftoverIndex(payerId, memberIds);
        LocalDateTime now = LocalDateTime.now(); // one timestamp for the whole split

        List<ExpenseShare> shares = new ArrayList<>();
        for (int i = 0; i < memberIds.size(); i++) {
            int userId = memberIds.get(i);
            BigDecimal owed = (i == leftoverIndex) ? baseShare.add(leftover) : baseShare;
            // The payer already paid the bill, so their own share is settled right away.
            LocalDateTime settledAt = (userId == payerId) ? now : null;
            shares.add(new ExpenseShare(0, 0, userId, owed, settledAt)); // 0 = database assigns ids later
        }
        return shares;
    }

    /**
     * The payer gets the leftover centavos because they are the one who is owed
     * money anyway, so the extra centavos stay with the person being paid back.
     * If the payer is not in the split, the first selected member takes them.
     */
    private int findLeftoverIndex(int payerId, List<Integer> memberIds) {
        int payerIndex = memberIds.indexOf(payerId);
        return payerIndex >= 0 ? payerIndex : 0;
    }

    private void validate(BigDecimal amount, int payerId, List<Integer> memberIds) {
        validateAmount(amount);
        if (payerId <= 0) throw new IllegalArgumentException("Please choose who paid.");
        validateMembers(memberIds);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount is required.");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
        if (amount.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Amount can have at most 2 decimal places.");
        if (amount.compareTo(MAX_AMOUNT) > 0) throw new IllegalArgumentException("Amount is too large.");
    }

    private void validateMembers(List<Integer> memberIds) {
        if (memberIds == null || memberIds.isEmpty())
            throw new IllegalArgumentException("Please choose at least one member to split with.");
        Set<Integer> seen = new HashSet<>();
        for (Integer id : memberIds) {
            if (id == null || id <= 0)
                throw new IllegalArgumentException("Every member id must be a positive number.");
            if (!seen.add(id))
                throw new IllegalArgumentException("A member can only be chosen once.");
        }
    }
}