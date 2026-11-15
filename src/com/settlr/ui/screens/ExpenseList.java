package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseShare;
import com.settlr.model.User;
import com.settlr.service.ExpenseService;
import com.settlr.service.HouseholdService;
import com.settlr.service.SettlementService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The household's expenses as the Expenses page shows them: every expense, everyone's share,
 * which shares have a payment waiting, and people's names. Loaded in one go on a background
 * thread (see ExpensesScreen.reload), then only read by the screen and its rows.
 */
final class ExpenseList {

    /** How far one person is with one expense. */
    enum ShareStatus { UNPAID, SENT, PAID }

    private final int me;
    private final List<Expense> expenses;
    private final Map<Integer, List<ExpenseShare>> sharesByExpense;
    private final Set<Integer> pendingShareIds;
    private final Map<Integer, String> names = new HashMap<>();

    /** Use load(...): it asks the services for these. */
    private ExpenseList(int me, List<Expense> expenses, Map<Integer, List<ExpenseShare>> sharesByExpense,
                        Set<Integer> pendingShareIds) {
        this.me = me;
        this.expenses = expenses;
        this.sharesByExpense = sharesByExpense;
        this.pendingShareIds = pendingShareIds;
    }

    /** Asks the services for everything. Database work: call it from a background load. */
    static ExpenseList load(int me, ExpenseService expenses, SettlementService settlements,
                            HouseholdService households) throws SettlrException {
        ExpenseList list = new ExpenseList(me, expenses.getExpenses(null), expenses.getSharesByExpense(),
                settlements.getPendingShareIds());
        for (User u : households.getMembers()) {
            list.names.put(u.getId(), u.getDisplayName());
        }
        for (Expense e : list.expenses) { // people who left still need their name
            list.remember(e.getPaidBy(), households);
            for (ExpenseShare s : list.sharesFor(e)) {
                list.remember(s.getUserId(), households);
            }
        }
        return list;
    }

    /** Looks up someone who left the household, so their name still shows. */
    private void remember(int userId, HouseholdService households) throws SettlrException {
        if (!names.containsKey(userId)) {
            User u = households.findUser(userId);
            names.put(userId, u == null ? "Someone" : u.getDisplayName());
        }
    }

    /** Every expense, newest first. */
    List<Expense> getExpenses() {
        return expenses;
    }

    /** "You" for the logged-in user, otherwise their name. */
    String nameOf(int userId) {
        return userId == me ? "You" : names.getOrDefault(userId, "Someone");
    }

    /** The logged-in user's id. */
    int getMe() {
        return me;
    }

    /** Everyone's share of this expense (empty if none were loaded). */
    List<ExpenseShare> sharesFor(Expense e) {
        return sharesByExpense.getOrDefault(e.getId(), new ArrayList<>());
    }

    /** Shares other people owe the payer (the payer's own share is never "owed"). */
    List<ExpenseShare> owedShares(Expense e) {
        List<ExpenseShare> result = new ArrayList<>();
        for (ExpenseShare s : sharesFor(e)) {
            if (s.getUserId() != e.getPaidBy() && s.getAmountOwed().signum() > 0) {
                result.add(s);
            }
        }
        return result;
    }

    /** True when every share someone owes for the expense is settled. */
    boolean isFullyPaid(Expense e) {
        for (ExpenseShare s : owedShares(e)) {
            if (!s.isSettled()) {
                return false;
            }
        }
        return true;
    }

    /** PAID if settled, SENT if a payment for it is waiting to be confirmed, otherwise UNPAID. */
    ShareStatus statusOf(ExpenseShare s) {
        if (s.isSettled()) {
            return ShareStatus.PAID;
        }
        return pendingShareIds.contains(s.getId()) ? ShareStatus.SENT : ShareStatus.UNPAID;
    }

    /** Your share of an expense, or null if you're not part of it. */
    BigDecimal myShare(Expense e) {
        for (ExpenseShare s : sharesFor(e)) {
            if (s.getUserId() == me) {
                return s.getAmountOwed();
            }
        }
        return null;
    }
}
