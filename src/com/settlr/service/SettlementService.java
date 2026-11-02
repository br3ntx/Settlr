package com.settlr.service;

import com.settlr.dao.ExpenseDao;
import com.settlr.dao.ExpenseShareDao;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.PaymentDao;
import com.settlr.dao.SettlementDao;
import com.settlr.exception.AuthException;
import com.settlr.exception.DataAccessException;
import com.settlr.exception.NotFoundException;
import com.settlr.exception.PermissionDeniedException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.ActivityType;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Payment;
import com.settlr.model.PaymentStatus;
import com.settlr.model.Settlement;
import com.settlr.model.User;
import com.settlr.util.DBConnection;
import com.settlr.util.MoneyUtil;
import com.settlr.util.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Settle up" between two people, paying the NET amount.
 *
 * Example: Ana owes Ben P6,000 (rent) and Ben owes Ana P800 (groceries). Ana settles up
 * with Ben for P5,200. That creates one Settlement plus a PENDING payment for every open
 * share in BOTH directions. When Ben confirms, all of them are confirmed together and both
 * shares are settled, in one transaction. If Ben rejects, nothing is settled.
 *
 * It also answers "where do I stand with each person?" for the Expenses page (getPeople).
 * Amounts follow BalanceService: a share counts as owed until a payment for it is confirmed.
 */
public class SettlementService {

    /** One open share between the current user and another person. Read-only. */
    public static final class ShareLine {
        private final int shareId;
        private final String expenseTitle;
        private final LocalDate dueDate;
        private final BigDecimal amount;
        private final boolean paymentPending;

        ShareLine(int shareId, String expenseTitle, LocalDate dueDate, BigDecimal amount, boolean paymentPending) {
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
        /** True when a payment for this share is already waiting to be confirmed. */
        public boolean isPaymentPending() { return paymentPending; }
    }

    /** Where the current user stands with one other member. Read-only. */
    public static final class Person {
        private final User user;
        private final List<ShareLine> iOwe;
        private final List<ShareLine> theyOwe;
        private final Settlement pendingFromMe;
        private final Settlement pendingToMe;
        private final BigDecimal sentSeparately;
        private final BigDecimal receivedSeparately;

        Person(User user, List<ShareLine> iOwe, List<ShareLine> theyOwe, Settlement pendingFromMe,
               Settlement pendingToMe, BigDecimal sentSeparately, BigDecimal receivedSeparately) {
            this.user = user;
            this.iOwe = Collections.unmodifiableList(iOwe);
            this.theyOwe = Collections.unmodifiableList(theyOwe);
            this.pendingFromMe = pendingFromMe;
            this.pendingToMe = pendingToMe;
            this.sentSeparately = sentSeparately;
            this.receivedSeparately = receivedSeparately;
        }

        public User getUser() { return user; }
        /** Open shares the current user owes this person. */
        public List<ShareLine> getIOwe() { return iOwe; }
        /** Open shares this person owes the current user. */
        public List<ShareLine> getTheyOwe() { return theyOwe; }
        public BigDecimal getIOweTotal() { return sum(iOwe); }
        public BigDecimal getTheyOweTotal() { return sum(theyOwe); }
        /** Positive: they owe me. Negative: I owe them. Zero: even. */
        public BigDecimal getNet() { return getTheyOweTotal().subtract(getIOweTotal()); }
        /** My settle up to them that they still have to confirm, or null. */
        public Settlement getPendingFromMe() { return pendingFromMe; }
        /** Their settle up to me that I still have to confirm, or null. */
        public Settlement getPendingToMe() { return pendingToMe; }
        /** Single-share payments I sent them (outside a settle up) that are still waiting. */
        public BigDecimal getSentSeparately() { return sentSeparately; }
        /** Single-share payments they sent me (outside a settle up) that I still have to confirm. */
        public BigDecimal getReceivedSeparately() { return receivedSeparately; }
        public boolean hasOpenShares() { return !iOwe.isEmpty() || !theyOwe.isEmpty(); }

        /** True when I'm the one who should settle up: I owe them (or we're even) and nothing is waiting. */
        public boolean canSettleUp() {
            if (pendingFromMe != null || pendingToMe != null || getNet().signum() > 0) {
                return false;
            }
            for (ShareLine line : iOwe) {
                if (!line.isPaymentPending()) {
                    return true;
                }
            }
            return getNet().signum() == 0 && !theyOwe.isEmpty();
        }

        /** The expense titles of all open shares, without repeats, e.g. "Rent, Groceries". */
        public List<String> getOpenTitles() {
            Set<String> titles = new LinkedHashSet<>();
            iOwe.forEach(l -> titles.add(l.getExpenseTitle()));
            theyOwe.forEach(l -> titles.add(l.getExpenseTitle()));
            return new ArrayList<>(titles);
        }

        private static BigDecimal sum(List<ShareLine> lines) {
            BigDecimal total = BigDecimal.ZERO;
            for (ShareLine l : lines) {
                total = total.add(l.getAmount());
            }
            return total;
        }
    }

    /** A settle up waiting for the current user to confirm, ready to show. Read-only. */
    public static final class Incoming {
        private final Settlement settlement;
        private final String fromName;
        private final String summary;

        Incoming(Settlement settlement, String fromName, String summary) {
            this.settlement = settlement;
            this.fromName = fromName;
            this.summary = summary;
        }

        public Settlement getSettlement() { return settlement; }
        public String getFromName() { return fromName; }
        /** What it clears, e.g. "Rent \u2212 Groceries" (their shares minus the ones that cancel out). */
        public String getSummary() { return summary; }
    }

    @FunctionalInterface
    private interface TransactionBody {
        void run(Connection c) throws SettlrException;
    }

    private final SettlementDao settlementDao = new SettlementDao();
    private final PaymentDao paymentDao = new PaymentDao();
    private final ExpenseShareDao shareDao = new ExpenseShareDao();
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final MembershipDao membershipDao = new MembershipDao();
    private final ActivityService activityService = new ActivityService();

    // =====================================================================
    //  Reading
    // =====================================================================

    /**
     * One Person per other active member: people I owe first (biggest first), then people
     * who owe me, then everyone who is settled up. Names A to Z inside each group.
     */
    public List<Person> getPeople() throws SettlrException {
        User me = currentUser();
        int householdId = currentHouseholdId();
        Context ctx = new Context(householdId);

        List<Person> people = new ArrayList<>();
        for (User other : membershipDao.findActiveMembers(householdId)) {
            if (other.getId() != me.getId()) {
                people.add(buildPerson(me, other, ctx));
            }
        }
        people.sort(Comparator.comparingInt(SettlementService::group)
                .thenComparing(p -> p.getNet().abs(), Comparator.reverseOrder())
                .thenComparing(p -> p.getUser().getDisplayName(), String.CASE_INSENSITIVE_ORDER));
        return people;
    }

    /** Where the current user stands with one member. */
    public Person getPerson(int otherUserId) throws SettlrException {
        User me = currentUser();
        int householdId = currentHouseholdId();
        for (User other : membershipDao.findActiveMembers(householdId)) {
            if (other.getId() == otherUserId && other.getId() != me.getId()) {
                return buildPerson(me, other, new Context(householdId));
            }
        }
        throw new NotFoundException("That person isn't an active member of your household.");
    }

    /**
     * Who this member still owes money to, after both directions cancel out, biggest first.
     * Each Person is seen from the member's side, so getNet() is negative (what they owe).
     * Used by the Household page: you can't leave while this isn't empty, and an Admin is
     * warned before removing someone who owes.
     */
    public List<Person> getDebtsOf(int userId) throws SettlrException {
        int householdId = currentHouseholdId();
        List<User> members = membershipDao.findActiveMembers(householdId);
        User target = null;
        for (User u : members) {
            if (u.getId() == userId) {
                target = u;
            }
        }
        if (target == null) {
            throw new NotFoundException("That person isn't an active member of your household.");
        }
        Context ctx = new Context(householdId);
        List<Person> owed = new ArrayList<>();
        for (User other : members) {
            if (other.getId() != userId) {
                Person p = buildPerson(target, other, ctx);
                if (p.getNet().signum() < 0) {
                    owed.add(p);
                }
            }
        }
        owed.sort(Comparator.comparing(Person::getNet)); // most negative (biggest debt) first
        return owed;
    }

    /** Settle ups the current user has to confirm or reject, oldest first. */
    public List<Incoming> getWaitingForMe() throws SettlrException {
        User me = currentUser();
        int householdId = currentHouseholdId();
        Map<Integer, String> names = memberNames(householdId);
        Map<Integer, Expense> expenses = new HashMap<>();

        List<Incoming> result = new ArrayList<>();
        for (Settlement s : settlementDao.findPendingTo(me.getId(), householdId)) {
            Set<String> paid = new LinkedHashSet<>();
            Set<String> cancelled = new LinkedHashSet<>();
            for (Payment p : paymentDao.findBySettlement(s.getId())) {
                String title = titleOf(p.getShareId(), expenses);
                (p.getFromUser() == s.getFromUser() ? paid : cancelled).add(title);
            }
            String summary = String.join(", ", paid)
                    + (cancelled.isEmpty() ? "" : " \u2212 " + String.join(", ", cancelled));
            result.add(new Incoming(s, names.getOrDefault(s.getFromUser(), "A former member"), summary));
        }
        return result;
    }

    /** Ids of shares in this household with a payment waiting to be confirmed (for "pending" labels). */
    public Set<Integer> getPendingShareIds() throws SettlrException {
        return paymentDao.findPendingShareIds(currentHouseholdId());
    }

    // =====================================================================
    //  Settle up, confirm, reject
    // =====================================================================

    /**
     * The current user pays otherUser the net amount. Every open share between them that
     * doesn't already have a payment waiting is included, in both directions.
     *
     * @param note optional, e.g. "GCash ref 1234"
     */
    public Settlement settleUp(int otherUserId, String note) throws SettlrException {
        return settleUp(otherUserId, note, null);
    }

    /**
     * Like settleUp(otherUserId, note), but only for the shares picked (ids of ShareLines, from
     * either direction). Null means all of them. The picked shares of yours must add up to at
     * least the picked shares of theirs, because the money goes from you to them.
     */
    public Settlement settleUp(int otherUserId, String note, Collection<Integer> onlyShareIds)
            throws SettlrException {
        User me = currentUser();
        int householdId = currentHouseholdId();
        Person person = getPerson(otherUserId);
        String name = person.getUser().getDisplayName();

        if (person.getPendingFromMe() != null) {
            throw new ValidationException("Your settle up with " + name + " is still waiting for them to confirm.");
        }
        if (person.getPendingToMe() != null) {
            throw new ValidationException(name + " already settled up with you. Confirm or reject it first.");
        }
        List<ShareLine> mine = picked(payable(person.getIOwe()), onlyShareIds);
        List<ShareLine> theirs = picked(payable(person.getTheyOwe()), onlyShareIds);
        if (onlyShareIds != null && mine.size() + theirs.size() < new LinkedHashSet<>(onlyShareIds).size()) {
            throw new ValidationException("Some of those expenses can't be settled right now. Please reopen and try again.");
        }
        if (mine.isEmpty() && theirs.isEmpty()) {
            throw new ValidationException(onlyShareIds == null
                    ? "There's nothing open between you and " + name + "."
                    : "Pick at least one expense to pay.");
        }
        BigDecimal amount = Person.sum(mine).subtract(Person.sum(theirs));
        if (amount.signum() < 0) {
            throw new ValidationException(onlyShareIds == null
                    ? name + " owes you more than you owe them, so " + name + " settles up with you."
                    : name + "'s shares you picked are more than yours. Pick more of yours, or fewer of "
                            + name + "'s.");
        }

        LocalDateTime now = LocalDateTime.now();
        Settlement settlement;
        try {
            settlement = new Settlement(0, householdId, me.getId(), otherUserId, amount, note, now,
                    PaymentStatus.PENDING, null);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
        List<Payment> payments = new ArrayList<>();
        for (ShareLine line : mine) {          // money I pay them
            payments.add(newPayment(line, me.getId(), otherUserId, now, settlement.getNote()));
        }
        for (ShareLine line : theirs) {        // their debts to me that cancel out
            payments.add(newPayment(line, otherUserId, me.getId(), now, "Cancelled out in a settle up"));
        }

        runInTransaction(c -> {
            int id = settlementDao.insert(c, settlement);
            for (Payment p : payments) {
                p.setSettlementId(id);
                p.setId(paymentDao.insert(c, p));
            }
        });
        return settlement;
    }

    /**
     * The receiver confirms: the settle up and all its payments become CONFIRMED, and every
     * share in it is settled (settled_at = when it was paid). All in ONE transaction.
     */
    public void confirm(int settlementId) throws SettlrException {
        Settlement s = loadPendingForReceiver(settlementId);
        LocalDateTime now = LocalDateTime.now();
        List<Payment> payments = paymentDao.findBySettlement(s.getId());

        runInTransaction(c -> {
            for (Payment p : payments) {
                if (p.getStatus() != PaymentStatus.PENDING) {
                    continue;
                }
                p.setStatus(PaymentStatus.CONFIRMED);
                p.setConfirmedAt(now);
                paymentDao.update(c, p);
                ExpenseShare share = shareDao.findById(p.getShareId());
                if (share != null && !share.isSettled()) {
                    share.setSettledAt(s.getCreatedAt());
                    shareDao.update(c, share);
                }
            }
            s.setStatus(PaymentStatus.CONFIRMED);
            s.setRespondedAt(now);
            settlementDao.update(c, s);
        });

        // Logged after the commit. If logging fails, the settle up IS already confirmed.
        Map<Integer, String> names = memberNames(s.getHouseholdId());
        String from = names.getOrDefault(s.getFromUser(), "Someone");
        String to = names.getOrDefault(s.getToUser(), "someone");
        String message = s.getAmount().signum() == 0
                ? from + " and " + to + " cancelled out what they owed each other"
                : from + " settled up with " + to + " (" + MoneyUtil.format(s.getAmount()) + ")";
        activityService.log(ActivityType.PAYMENT_CONFIRMED, s.getFromUser(), message);
    }

    /** The receiver rejects: nothing is settled, and the shares can be settled up again. */
    public void reject(int settlementId) throws SettlrException {
        Settlement s = loadPendingForReceiver(settlementId);
        LocalDateTime now = LocalDateTime.now();
        List<Payment> payments = paymentDao.findBySettlement(s.getId());
        runInTransaction(c -> {
            for (Payment p : payments) {
                if (p.getStatus() == PaymentStatus.PENDING) {
                    p.setStatus(PaymentStatus.REJECTED);
                    paymentDao.update(c, p);
                }
            }
            s.setStatus(PaymentStatus.REJECTED);
            s.setRespondedAt(now);
            settlementDao.update(c, s);
        });
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /** Data loaded once per call and shared by every Person. */
    private final class Context {
        final int householdId;
        final Set<Integer> pendingShareIds;
        final List<Settlement> pendingSettlements;
        final Map<Integer, Expense> expenses = new HashMap<>();

        Context(int householdId) throws SettlrException {
            this.householdId = householdId;
            this.pendingShareIds = paymentDao.findPendingShareIds(householdId);
            this.pendingSettlements = settlementDao.findPendingInHousehold(householdId);
        }
    }

    private Person buildPerson(User me, User other, Context ctx) throws SettlrException {
        List<ShareLine> iOwe = lines(me.getId(), other.getId(), ctx);
        List<ShareLine> theyOwe = lines(other.getId(), me.getId(), ctx);

        Settlement fromMe = null, toMe = null;
        for (Settlement s : ctx.pendingSettlements) {
            if (s.getFromUser() == me.getId() && s.getToUser() == other.getId()) {
                fromMe = s;
            } else if (s.getFromUser() == other.getId() && s.getToUser() == me.getId()) {
                toMe = s;
            }
        }
        return new Person(other, iOwe, theyOwe, fromMe, toMe, sentSeparately(iOwe), sentSeparately(theyOwe));
    }

    /** Total of the single-share payments (not part of a settle up) still waiting on these shares. */
    private BigDecimal sentSeparately(List<ShareLine> lines) throws SettlrException {
        BigDecimal total = BigDecimal.ZERO;
        for (ShareLine line : lines) {
            if (line.isPaymentPending()) {
                Payment p = paymentDao.findPendingForShare(line.getShareId());
                if (p != null && p.getSettlementId() == null) {
                    total = total.add(p.getAmount());
                }
            }
        }
        return total;
    }

    /** Open shares debtor owes creditor in this household, oldest due date first. */
    private List<ShareLine> lines(int debtorId, int creditorId, Context ctx) throws SettlrException {
        List<ShareLine> result = new ArrayList<>();
        for (ExpenseShare share : shareDao.findUnsettled(debtorId, creditorId)) {
            if (share.getAmountOwed().signum() <= 0) {
                continue; // nothing to pay
            }
            Expense e = expense(share.getExpenseId(), ctx.expenses);
            if (e == null || e.getHouseholdId() != ctx.householdId) {
                continue;
            }
            result.add(new ShareLine(share.getId(), e.getTitle(), e.getDueDate(), share.getAmountOwed(),
                    ctx.pendingShareIds.contains(share.getId())));
        }
        return result;
    }

    /** 0 = I owe them, 1 = they owe me, 2 = even but open shares, 3 = settled up. */
    private static int group(Person p) {
        int sign = p.getNet().signum();
        if (sign < 0) return 0;
        if (sign > 0) return 1;
        return p.hasOpenShares() ? 2 : 3;
    }

    /** Only the lines whose share id was picked; all of them when ids is null. */
    private static List<ShareLine> picked(List<ShareLine> lines, Collection<Integer> ids) {
        if (ids == null) {
            return lines;
        }
        List<ShareLine> result = new ArrayList<>();
        for (ShareLine l : lines) {
            if (ids.contains(l.getShareId())) {
                result.add(l);
            }
        }
        return result;
    }

    private static List<ShareLine> payable(List<ShareLine> lines) {
        List<ShareLine> result = new ArrayList<>();
        for (ShareLine l : lines) {
            if (!l.isPaymentPending()) {
                result.add(l);
            }
        }
        return result;
    }

    private static Payment newPayment(ShareLine line, int from, int to, LocalDateTime now, String note)
            throws ValidationException {
        try {
            return new Payment(0, line.getShareId(), from, to, line.getAmount(), now,
                    PaymentStatus.PENDING, null, note);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
    }

    private Settlement loadPendingForReceiver(int settlementId) throws SettlrException {
        User me = currentUser();
        int householdId = currentHouseholdId();
        Settlement s = settlementDao.findById(settlementId);
        if (s == null || s.getHouseholdId() != householdId) {
            throw new NotFoundException("That settle up could not be found.");
        }
        if (s.getToUser() != me.getId()) {
            throw new PermissionDeniedException("Only the person being paid can confirm or reject it.");
        }
        if (!s.isPending()) {
            throw new ValidationException("This settle up was already " + s.getStatus().name().toLowerCase() + ".");
        }
        return s;
    }

    private Expense expense(int expenseId, Map<Integer, Expense> cache) throws SettlrException {
        if (!cache.containsKey(expenseId)) {
            cache.put(expenseId, expenseDao.findById(expenseId));
        }
        return cache.get(expenseId);
    }

    private String titleOf(int shareId, Map<Integer, Expense> cache) throws SettlrException {
        ExpenseShare share = shareDao.findById(shareId);
        Expense e = share == null ? null : expense(share.getExpenseId(), cache);
        return e == null ? "an expense" : e.getTitle();
    }

    private Map<Integer, String> memberNames(int householdId) throws SettlrException {
        Map<Integer, String> names = new HashMap<>();
        for (User u : membershipDao.findActiveMembers(householdId)) {
            names.put(u.getId(), u.getDisplayName());
        }
        return names;
    }

    private void runInTransaction(TransactionBody body) throws SettlrException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            c.setAutoCommit(false);
            try {
                body.run(c);
                c.commit();
            } catch (SettlrException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the settle up. Please try again.", e);
        }
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
