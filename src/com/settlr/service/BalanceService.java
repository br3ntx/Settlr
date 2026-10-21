package com.settlr.service;

import com.settlr.dao.ExpenseDao;
import com.settlr.dao.ExpenseShareDao;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.PaymentDao;
import com.settlr.exception.AuthException;
import com.settlr.exception.PermissionDeniedException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.Balance;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Payment;
import com.settlr.model.User;
import com.settlr.util.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out who owes whom (master doc 3.6). A balance is the NET between the current
 * user and one other member: what they owe me in unsettled shares minus what I owe them.
 * Only CONFIRMED payments settle a share, so a PENDING payment never lowers a balance.
 * Read-only: it never changes data, and it contains no SQL (the DAOs do that).
 */
public class BalanceService {

    /** One unsettled share, ready to show in PaymentDialog. Read-only so the UI cannot change it. */
    public static final class ShareRow {
        private final int shareId;
        private final String expenseTitle;
        private final LocalDate dueDate;
        private final BigDecimal amount;
        private final boolean paymentPending;

        ShareRow(int shareId, String expenseTitle, LocalDate dueDate,
                 BigDecimal amount, boolean paymentPending) {
            this.shareId = shareId;
            this.expenseTitle = expenseTitle;
            this.dueDate = dueDate;
            this.amount = amount;
            this.paymentPending = paymentPending;
        }

        public int getShareId() { return shareId; }
        public String getExpenseTitle() { return expenseTitle; }
        public LocalDate getDueDate() { return dueDate; }
        public BigDecimal getAmount() { return amount; }

        /** True when a payment for this share is already waiting for the receiver to confirm. */
        public boolean isPaymentPending() { return paymentPending; }
    }

    /** One PENDING payment waiting for the current user to confirm or reject. Read-only. */
    public static final class PaymentRow {
        private final int paymentId;
        private final String fromUserName;
        private final String expenseTitle;
        private final BigDecimal amount;
        private final LocalDateTime paidAt;

        PaymentRow(int paymentId, String fromUserName, String expenseTitle,
                   BigDecimal amount, LocalDateTime paidAt) {
            this.paymentId = paymentId;
            this.fromUserName = fromUserName;
            this.expenseTitle = expenseTitle;
            this.amount = amount;
            this.paidAt = paidAt;
        }

        public int getPaymentId() { return paymentId; }
        public String getFromUserName() { return fromUserName; }
        public String getExpenseTitle() { return expenseTitle; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getPaidAt() { return paidAt; }
    }

    private final ExpenseShareDao shareDao = new ExpenseShareDao();
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final PaymentDao paymentDao = new PaymentDao();
    private final MembershipDao membershipDao = new MembershipDao();

    // ---- public API (master doc 8.4) -----------------------------------

    /**
     * One Balance per OTHER active member of the household (zero balances included, so the
     * screen can show "all settled"). Positive = they owe me, negative = I owe them.
     */
    public List<Balance> getMyBalances() throws SettlrException {
        int me = currentUser().getId();
        int householdId = currentHouseholdId();
        Map<Integer, Expense> expenseCache = new HashMap<>();

        List<Balance> balances = new ArrayList<>();
        for (User member : membershipDao.findActiveMembers(householdId)) {
            if (member.getId() == me) {
                continue;
            }
            BigDecimal theyOweMe = sumUnsettled(member.getId(), me, householdId, expenseCache);
            BigDecimal iOweThem = sumUnsettled(me, member.getId(), householdId, expenseCache);
            balances.add(new Balance(member, theyOweMe.subtract(iOweThem)));
        }
        // People with a balance first, then settled ones; alphabetical inside each group.
        balances.sort(Comparator.comparing((Balance b) -> b.isZero())
                .thenComparing(b -> b.getOtherUser().getDisplayName(), String.CASE_INSENSITIVE_ORDER));
        return balances;
    }

    /** Sum of what I owe, counting only people I owe a net amount to. */
    public BigDecimal getTotalIOwe() throws SettlrException {
        BigDecimal total = BigDecimal.ZERO;
        for (Balance b : getMyBalances()) {
            if (b.iOweThem()) {
                total = total.add(b.getAbsoluteAmount());
            }
        }
        return total;
    }

    /** Sum of what others owe me, counting only people who owe me a net amount. */
    public BigDecimal getTotalOwedToMe() throws SettlrException {
        BigDecimal total = BigDecimal.ZERO;
        for (Balance b : getMyBalances()) {
            if (b.theyOweMe()) {
                total = total.add(b.getAbsoluteAmount());
            }
        }
        return total;
    }

    // ---- extra read methods for the screens (not in master doc 8.4) ----

    /**
     * The unsettled shares the current user owes to one person, oldest due date first.
     * Used by PaymentDialog. Shares with a pending payment are included but flagged.
     */
    public List<ShareRow> getUnsettledSharesOwedTo(int creditorId) throws SettlrException {
        int me = currentUser().getId();
        int householdId = currentHouseholdId();
        if (creditorId == me) {
            throw new ValidationException("You cannot pay yourself.");
        }
        if (!isActiveMember(householdId, creditorId)) {
            throw new ValidationException("That person is not an active member of your household.");
        }

        Map<Integer, Expense> expenseCache = new HashMap<>();
        List<ShareRow> rows = new ArrayList<>();
        for (ExpenseShare share : shareDao.findUnsettled(me, creditorId)) {
            if (share.getAmountOwed().signum() <= 0) {
                continue; // a P0.00 share has nothing to pay (see note in the README)
            }
            Expense expense = expenseOf(share, expenseCache);
            if (expense == null || expense.getHouseholdId() != householdId) {
                continue;
            }
            boolean pending = paymentDao.findPendingForShare(share.getId()) != null;
            rows.add(new ShareRow(share.getId(), expense.getTitle(), expense.getDueDate(),
                    share.getAmountOwed(), pending));
        }
        return rows;
    }

    /** The PENDING payments the current user still has to confirm or reject, oldest first. */
    public List<PaymentRow> getIncomingPaymentRows() throws SettlrException {
        int me = currentUser().getId();
        int householdId = currentHouseholdId();
        Map<Integer, String> names = memberNames(householdId);
        Map<Integer, Expense> expenseCache = new HashMap<>();

        List<PaymentRow> rows = new ArrayList<>();
        for (Payment p : paymentDao.findWaitingFor(me)) {
            ExpenseShare share = shareDao.findById(p.getShareId());
            if (share == null) {
                continue;
            }
            Expense expense = expenseOf(share, expenseCache);
            if (expense == null || expense.getHouseholdId() != householdId) {
                continue;
            }
            String from = names.getOrDefault(p.getFromUser(), "A former member");
            rows.add(new PaymentRow(p.getId(), from, expense.getTitle(), p.getAmount(), p.getPaidAt()));
        }
        return rows;
    }

    // ---- helpers -------------------------------------------------------

    /**
     * Adds up the unsettled shares debtorId owes for expenses creditorId paid, inside this
     * household only. A share counts once: settled shares are excluded by the DAO query,
     * and PENDING payments are deliberately ignored.
     */
    private BigDecimal sumUnsettled(int debtorId, int creditorId, int householdId,
                                    Map<Integer, Expense> expenseCache) throws SettlrException {
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseShare share : shareDao.findUnsettled(debtorId, creditorId)) {
            if (share.getAmountOwed().signum() <= 0) {
                continue;
            }
            Expense expense = expenseOf(share, expenseCache);
            if (expense == null || expense.getHouseholdId() != householdId) {
                continue;
            }
            total = total.add(share.getAmountOwed());
        }
        return total;
    }

    /** Loads an expense once per call; the cache avoids repeating the same query. */
    private Expense expenseOf(ExpenseShare share, Map<Integer, Expense> cache) throws SettlrException {
        Integer id = share.getExpenseId();
        if (!cache.containsKey(id)) {
            cache.put(id, expenseDao.findById(id));
        }
        return cache.get(id);
    }

    private boolean isActiveMember(int householdId, int userId) throws SettlrException {
        for (User member : membershipDao.findActiveMembers(householdId)) {
            if (member.getId() == userId) {
                return true;
            }
        }
        return false;
    }

    private Map<Integer, String> memberNames(int householdId) throws SettlrException {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (User member : membershipDao.findActiveMembers(householdId)) {
            names.put(member.getId(), member.getDisplayName());
        }
        return names;
    }

    private User currentUser() throws SettlrException {
        Session session = Session.getInstance();
        if (!session.isLoggedIn()) {
            throw new AuthException("Please log in first.");
        }
        return session.getCurrentUser();
    }

    private int currentHouseholdId() throws SettlrException {
        currentUser();
        if (Session.getInstance().getCurrentHousehold() == null) {
            throw new PermissionDeniedException("Please create or join a household first.");
        }
        return Session.getInstance().getCurrentHousehold().getId();
    }
}