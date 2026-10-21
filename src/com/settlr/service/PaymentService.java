package com.settlr.service;

import com.settlr.dao.ExpenseDao;
import com.settlr.dao.ExpenseShareDao;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.PaymentDao;
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
import com.settlr.model.User;
import com.settlr.util.DBConnection;
import com.settlr.util.MoneyUtil;
import com.settlr.util.Session;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The payment lifecycle (master doc 3.6): the debtor records a PENDING payment per share;
 * only the receiver can CONFIRM it (the share becomes settled) or REJECT it (the share stays
 * unsettled). Creating a payment never settles anything by itself. Checks permissions here,
 * never trusts the UI, and groups related writes in one transaction. No SQL lives here.
 */
public class PaymentService {

    private final PaymentDao paymentDao = new PaymentDao();
    private final ExpenseShareDao shareDao = new ExpenseShareDao();
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final MembershipDao membershipDao = new MembershipDao();
    private final ActivityService activityService = new ActivityService();

    /** One step of a transaction; receives the shared connection. */
    private interface TransactionBody {
        void run(Connection c) throws SettlrException;
    }

    /** A payment together with its share and expense, loaded and checked once. */
    private static final class Loaded {
        final Payment payment;
        final ExpenseShare share;
        final Expense expense;

        Loaded(Payment payment, ExpenseShare share, Expense expense) {
            this.payment = payment;
            this.share = share;
            this.expense = expense;
        }
    }

    // ---- public API (master doc 8.4) -----------------------------------

    /**
     * The current user (the debtor) pays the given shares to ONE person. Each share gets
     * a payment with status PENDING and paid_at = now. Nothing is settled yet. All payments
     * are saved together or not at all.
     */
    public void pay(List<Integer> shareIds) throws SettlrException {
        User me = currentUser();
        int householdId = currentHouseholdId();
        validateShareIds(shareIds);

        Map<Integer, String> activeMembers = memberNames(householdId);
        LocalDateTime now = LocalDateTime.now();
        List<Payment> payments = new ArrayList<>();
        Integer creditorId = null;

        for (int shareId : shareIds) {
            ExpenseShare share = shareDao.findById(shareId);
            if (share == null) {
                throw new NotFoundException("One of the shares could not be found.");
            }
            Expense expense = requireExpenseInHousehold(share, householdId);

            if (share.getUserId() != me.getId()) {
                throw new PermissionDeniedException("You can only pay your own shares.");
            }
            if (share.isSettled()) {
                throw new ValidationException("\"" + expense.getTitle() + "\" is already settled.");
            }
            if (share.getAmountOwed().signum() <= 0) {
                throw new ValidationException("\"" + expense.getTitle() + "\" has nothing to pay.");
            }
            int payeeId = expense.getPaidBy();
            if (payeeId == me.getId()) {
                throw new ValidationException("You cannot pay yourself.");
            }
            if (!activeMembers.containsKey(payeeId)) {
                throw new ValidationException("The person you are paying is no longer an active member.");
            }
            if (creditorId == null) {
                creditorId = payeeId;
            } else if (creditorId.intValue() != payeeId) {
                throw new ValidationException("Please pay one person at a time.");
            }
            // Rule 3.6: a share can have only one PENDING payment at a time.
            if (paymentDao.findPendingForShare(shareId) != null) {
                throw new ValidationException("\"" + expense.getTitle()
                        + "\" already has a payment waiting for confirmation.");
            }
            payments.add(newPendingPayment(shareId, me.getId(), payeeId, share, now));
        }

        runInTransaction(c -> {
            for (Payment p : payments) {
                p.setId(paymentDao.insert(c, p));
            }
        });
    }

    /**
     * The receiver confirms: payment becomes CONFIRMED and the share's settled_at = the
     * payment's paid_at (rule 3.6). Both updates happen in ONE transaction.
     */
    public void confirm(int paymentId) throws SettlrException {
        int householdId = currentHouseholdId();
        Loaded loaded = loadPendingForReceiver(paymentId, householdId);
        Payment payment = loaded.payment;
        ExpenseShare share = loaded.share;

        if (share.isSettled()) {
            throw new ValidationException("This share is already settled.");
        }
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setConfirmedAt(LocalDateTime.now());
        share.setSettledAt(payment.getPaidAt());

        runInTransaction(c -> {
            paymentDao.update(c, payment);
            shareDao.update(c, share);
        });

        // Logged after the commit. If logging fails, the payment IS already confirmed.
        Map<Integer, String> names = memberNames(householdId);
        String message = nameOf(names, payment.getFromUser()) + " paid "
                + nameOf(names, payment.getToUser()) + " " + MoneyUtil.format(payment.getAmount())
                + " for " + loaded.expense.getTitle();
        activityService.log(ActivityType.PAYMENT_CONFIRMED, payment.getFromUser(), message);
    }

    /** The receiver rejects: payment becomes REJECTED and the share stays unsettled. */
    public void reject(int paymentId) throws SettlrException {
        Loaded loaded = loadPendingForReceiver(paymentId, currentHouseholdId());
        loaded.payment.setStatus(PaymentStatus.REJECTED);
        // One statement changes one row, so no transaction is needed.
        paymentDao.update(loaded.payment);
    }

    /** PENDING payments the current user must still confirm or reject. */
    public List<Payment> getWaitingForMyConfirmation() throws SettlrException {
        int me = currentUser().getId();
        int householdId = currentHouseholdId();

        List<Payment> mine = new ArrayList<>();
        for (Payment p : paymentDao.findWaitingFor(me)) {
            ExpenseShare share = shareDao.findById(p.getShareId());
            if (share == null) {
                continue;
            }
            Expense expense = expenseDao.findById(share.getExpenseId());
            if (expense != null && expense.getHouseholdId() == householdId) {
                mine.add(p);
            }
        }
        return mine;
    }

    // ---- validation and permissions ------------------------------------

    private void validateShareIds(List<Integer> shareIds) throws ValidationException {
        if (shareIds == null || shareIds.isEmpty()) {
            throw new ValidationException("Please tick at least one share to pay.");
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer id : shareIds) {
            if (id == null || id <= 0) {
                throw new ValidationException("One of the chosen shares is not valid.");
            }
            if (!seen.add(id)) {
                throw new ValidationException("Each share can only be ticked once.");
            }
        }
    }

    /**
     * Shared checks for confirm and reject, in order: the payment exists and belongs to this
     * household, the current user is the receiver, and it is still PENDING (so a payment is
     * never processed twice).
     */
    private Loaded loadPendingForReceiver(int paymentId, int householdId) throws SettlrException {
        int me = currentUser().getId();
        Payment payment = paymentDao.findById(paymentId);
        if (payment == null) {
            throw new NotFoundException("That payment could not be found.");
        }
        ExpenseShare share = shareDao.findById(payment.getShareId());
        if (share == null) {
            throw new NotFoundException("That payment could not be found.");
        }
        Expense expense = requireExpenseInHousehold(share, householdId);

        if (payment.getToUser() != me) {
            throw new PermissionDeniedException(
                    "Only the person who received this payment can confirm or reject it.");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ValidationException("This payment was already "
                    + payment.getStatus().name().toLowerCase() + ".");
        }
        return new Loaded(payment, share, expense);
    }

    /** Another household's data is reported as "not found" so its existence is not leaked. */
    private Expense requireExpenseInHousehold(ExpenseShare share, int householdId) throws SettlrException {
        Expense expense = expenseDao.findById(share.getExpenseId());
        if (expense == null || expense.getHouseholdId() != householdId) {
            throw new NotFoundException("That share could not be found.");
        }
        return expense;
    }

    // ---- session -------------------------------------------------------

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

    // ---- helpers -------------------------------------------------------

    /** The Payment constructor validates; its IllegalArgumentException becomes a readable ValidationException. */
    private Payment newPendingPayment(int shareId, int fromUser, int toUser,
                                      ExpenseShare share, LocalDateTime now) throws ValidationException {
        try {
            return new Payment(0, shareId, fromUser, toUser, share.getAmountOwed(),
                    now, PaymentStatus.PENDING, null, null);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ex.getMessage());
        }
    }

    private Map<Integer, String> memberNames(int householdId) throws SettlrException {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (User member : membershipDao.findActiveMembers(householdId)) {
            names.put(member.getId(), member.getDisplayName());
        }
        return names;
    }

    private String nameOf(Map<Integer, String> names, int userId) {
        return names.getOrDefault(userId, "A member");
    }

    /**
     * Runs several DAO calls as one all-or-nothing unit (rule in section 10). The service
     * decides where the transaction starts and ends; the DAOs still hold every SQL statement.
     */
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
            throw new DataAccessException("Could not save your payment. Please try again.");
        }
    }
}