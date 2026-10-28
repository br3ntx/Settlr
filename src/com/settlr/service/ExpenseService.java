package com.settlr.service;

import com.settlr.dao.ExpenseDao;
import com.settlr.dao.ExpenseShareDao;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.PaymentDao;
import com.settlr.dao.RecurringBillDao;
import com.settlr.exception.AuthException;
import com.settlr.exception.DataAccessException;
import com.settlr.exception.NotFoundException;
import com.settlr.exception.PermissionDeniedException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;
import com.settlr.model.ExpenseShare;
import com.settlr.model.RecurringBill;
import com.settlr.model.User;
import com.settlr.split.EqualSplit;
import com.settlr.split.SplitStrategy;
import com.settlr.util.DBConnection;
import com.settlr.util.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business rules for expenses and recurring bills: validates input, splits the amount
 * with a SplitStrategy, and saves an expense together with its shares in ONE transaction.
 * It never writes SQL; the DAOs do that. Payments (pay/confirm/reject) belong to PaymentService.
 */
public class ExpenseService {

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final RecurringBillDao recurringBillDao = new RecurringBillDao();
    private final ExpenseShareDao expenseShareDao = new ExpenseShareDao();
    private final PaymentDao paymentDao = new PaymentDao();
    private final MembershipDao membershipDao = new MembershipDao();

    // ---------- public API (signatures from master doc section 8.4) ----------

    /**
     * Adds an expense for the current household and creates one share per member in
     * splitAmong, using the given strategy. The payer's own share is settled immediately.
     */
    public Expense addExpense(Expense e, List<Integer> splitAmong, SplitStrategy strategy)
            throws SettlrException {
        requireLoggedIn();
        if (e == null) {
            throw new ValidationException("Expense is required.");
        }
        if (strategy == null) {
            throw new ValidationException("Please choose how to split the expense.");
        }
        validateAmount(e.getAmount());
        validateMemberIds(splitAmong);

        int householdId = Session.getInstance().getCurrentHousehold().getId();
        int userId = Session.getInstance().getCurrentUser().getId();
        // The form doesn't know these two, so the service fills them in from the session.
        e.setHouseholdId(householdId);
        e.setCreatedBy(userId);

        requireActiveMembers(householdId, e.getPaidBy(), splitAmong);
        List<ExpenseShare> shares = buildShares(e.getAmount(), e.getPaidBy(), splitAmong, strategy);
        saveExpenseWithShares(e, shares, null);
        return e;
    }

    /** Saves a recurring bill (and who it is split among) for the current household. */
    public RecurringBill createRecurringBill(RecurringBill b) throws SettlrException {
        requireLoggedIn();
        if (b == null) {
            throw new ValidationException("Recurring bill is required.");
        }
        validateAmount(b.getAmount());
        validateMemberIds(b.getMemberIds());
        if (b.getDueDay() < 1 || b.getDueDay() > 28) {
            throw new ValidationException("Due day must be between 1 and 28.");
        }

        int householdId = Session.getInstance().getCurrentHousehold().getId();
        b.setHouseholdId(householdId);
        b.setCreatedBy(Session.getInstance().getCurrentUser().getId());
        requireActiveMembers(householdId, b.getPaidBy(), b.getMemberIds());

        b.setId(recurringBillDao.insert(b));
        return b;
    }

    /**
     * Deletes an expense. Allowed for its creator or an Admin, and only while none of its
     * shares has a confirmed payment (rule 3.5).
     */
    public void deleteExpense(int expenseId) throws SettlrException {
        requireLoggedIn();
        Expense expense = expenseDao.findById(expenseId);
        int householdId = Session.getInstance().getCurrentHousehold().getId();
        // Another household's expense is reported as "not found" so its existence isn't leaked.
        if (expense == null || expense.getHouseholdId() != householdId) {
            throw new NotFoundException("That expense could not be found.");
        }
        boolean isCreator = expense.getCreatedBy() == Session.getInstance().getCurrentUser().getId();
        if (!isCreator && !Session.getInstance().isAdmin()) {
            throw new PermissionDeniedException(
                    "Only the person who added this expense or an Admin can delete it.");
        }
        if (paymentDao.existsConfirmedForExpense(expenseId)) {
            throw new ValidationException(
                    "This expense can't be deleted because a payment was already confirmed.");
        }
        expenseDao.delete(expenseId);
    }

    /** Expenses of the current household, newest first. Pass null to include every category. */
    public List<Expense> getExpenses(ExpenseCategory filterOrNull) throws SettlrException {
        requireLoggedIn();
        return expenseDao.findByHousehold(
                Session.getInstance().getCurrentHousehold().getId(), filterOrNull);
    }

    /** Each expense's shares, keyed by expense id (the Expenses page shows "your share" from this). */
    public Map<Integer, List<ExpenseShare>> getSharesByExpense() throws SettlrException {
        requireLoggedIn();
        Map<Integer, List<ExpenseShare>> byExpense = new HashMap<>();
        int householdId = Session.getInstance().getCurrentHousehold().getId();
        for (ExpenseShare share : expenseShareDao.findByHousehold(householdId)) {
            byExpense.computeIfAbsent(share.getExpenseId(), id -> new ArrayList<>()).add(share);
        }
        return byExpense;
    }

    /**
     * Startup catch-up step 4: for each active recurring bill, create this month's expense
     * if it doesn't exist yet. Safe to run twice: the second run finds the expense and skips.
     * Needs no logged-in user, because it runs before the login screen.
     */
    public void generateDueBills(LocalDate today) throws SettlrException {
        if (today == null) {
            throw new ValidationException("Date is required.");
        }
        for (RecurringBill bill : recurringBillDao.findActive()) {
            // due_day is 1-28, so withDayOfMonth works in every month (even February).
            LocalDate dueDate = today.withDayOfMonth(bill.getDueDay());
            if (expenseDao.existsForBill(bill.getId(), dueDate)) {
                continue;
            }
            Expense expense = bill.createExpenseFor(YearMonth.from(today));
            List<ExpenseShare> shares = buildShares(
                    expense.getAmount(), expense.getPaidBy(), bill.getMemberIds(), new EqualSplit());
            saveExpenseWithShares(expense, shares, bill);
        }
    }

    // ---------- validation helpers ----------

    private void requireLoggedIn() throws AuthException {
        if (!Session.getInstance().isLoggedIn()) {
            throw new AuthException("Please log in first.");
        }
    }

    private void validateAmount(BigDecimal amount) throws ValidationException {
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("Amount must be more than \u20B10.");
        }
        // stripTrailingZeros so 100.50 and 100.5000 are both fine
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ValidationException("Amount can have at most 2 decimal places.");
        }
    }

    private void validateMemberIds(List<Integer> memberIds) throws ValidationException {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new ValidationException("Please pick at least one member to split with.");
        }
        Set<Integer> unique = new HashSet<>();
        for (Integer id : memberIds) {
            if (id == null || id <= 0) {
                throw new ValidationException("One of the chosen members is not valid.");
            }
            if (!unique.add(id)) {
                throw new ValidationException("Each member can only be picked once.");
            }
        }
    }

    /** The payer and everyone in the split must be ACTIVE members of this household. */
    private void requireActiveMembers(int householdId, int payerId, List<Integer> memberIds)
            throws SettlrException {
        Set<Integer> active = membershipDao.findActiveMembers(householdId).stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        if (!active.contains(payerId)) {
            throw new ValidationException("The person who paid must be an active member.");
        }
        for (int id : memberIds) {
            if (!active.contains(id)) {
                throw new ValidationException("You can only split with active members of your household.");
            }
        }
    }

    // ---------- splitting ----------

    /**
     * Asks the strategy for the shares, double-checks them, and settles the payer's own share.
     * The check protects the books: if a strategy ever lost a centavo, we refuse to save.
     */
    private List<ExpenseShare> buildShares(BigDecimal amount, int payerId, List<Integer> memberIds,
                                           SplitStrategy strategy) throws ValidationException {
        validateMemberIds(memberIds);
        List<ExpenseShare> shares = strategy.split(amount, payerId, memberIds);
        if (shares == null || shares.size() != memberIds.size()) {
            throw new ValidationException("The amount could not be split. Please try again.");
        }
        BigDecimal total = BigDecimal.ZERO;
        Set<Integer> seen = new HashSet<>();
        for (ExpenseShare share : shares) {
            if (!memberIds.contains(share.getUserId()) || !seen.add(share.getUserId())) {
                throw new ValidationException("The amount could not be split. Please try again.");
            }
            total = total.add(share.getAmountOwed());
        }
        if (total.compareTo(amount) != 0) {
            throw new ValidationException("The shares do not add up to the expense amount.");
        }
        LocalDateTime now = LocalDateTime.now();
        for (ExpenseShare share : shares) {
            if (share.getUserId() == payerId && !share.isSettled()) {
                share.setSettledAt(now); // the payer doesn't owe themselves (rule 3.5)
            }
        }
        return shares;
    }

    // ---------- transaction ----------

    /**
     * Saves the expense and ALL its shares in one transaction: either everything is saved
     * or nothing is. If billOrNull is given, its last_generated is updated in the same transaction.
     */
    private void saveExpenseWithShares(Expense expense, List<ExpenseShare> shares, RecurringBill billOrNull)
            throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection()) {
            try {
                con.setAutoCommit(false); // start the transaction
                int expenseId = expenseDao.insert(con, expense);
                expense.setId(expenseId);
                for (ExpenseShare share : shares) {
                    share.setExpenseId(expenseId); // the shares didn't know the id until now
                    expenseShareDao.insert(con, share);
                }
                if (billOrNull != null) {
                    recurringBillDao.updateLastGenerated(con, billOrNull.getId(), expense.getDueDate());
                }
                con.commit(); // make it permanent
            } catch (DataAccessException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            } catch (SQLException ex) {
                rollback(con, ex);
                throw new DataAccessException("Could not save the expense.", ex);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the expense.", ex);
        }
    }

    /** Undoes the transaction; if even the undo fails, keep that error attached to the first one. */
    private void rollback(Connection con, Exception original) {
        try {
            con.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}