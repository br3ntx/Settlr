package com.settlr.dao.memory;

import com.settlr.dao.PaymentDao;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Payment;
import com.settlr.model.PaymentStatus;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Payments kept in a list. */
class InMemoryPaymentDao implements PaymentDao {

    private final InMemoryDatabase db;

    InMemoryPaymentDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Payment findById(int id) {
        for (Payment p : db.payments) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }

    @Override
    public int insert(Payment p) {
        p.setId(db.newId());
        db.payments.add(p);
        return p.getId();
    }

    @Override
    public int insert(Connection c, Payment p) {
        return insert(p);
    }

    @Override
    public void update(Payment p) {
        delete(p.getId());
        db.payments.add(p);
    }

    @Override
    public void update(Connection c, Payment p) {
        update(p);
    }

    @Override
    public void delete(int id) {
        db.payments.removeIf(p -> p.getId() == id);
    }

    @Override
    public List<Payment> findWaitingFor(int receiverId) {
        List<Payment> result = new ArrayList<>();
        for (Payment p : db.payments) {
            if (p.getToUser() == receiverId && p.getStatus() == PaymentStatus.PENDING && p.getSettlementId() == null) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public Payment findPendingForShare(int shareId) {
        for (Payment p : db.payments) {
            if (p.getShareId() == shareId && p.getStatus() == PaymentStatus.PENDING) {
                return p;
            }
        }
        return null;
    }

    @Override
    public boolean existsConfirmedForExpense(int expenseId) {
        for (Payment p : db.payments) {
            ExpenseShare s = db.share(p.getShareId());
            if (s != null && s.getExpenseId() == expenseId && p.getStatus() == PaymentStatus.CONFIRMED) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Payment> findBySettlement(int settlementId) {
        List<Payment> result = new ArrayList<>();
        for (Payment p : db.payments) {
            if (Objects.equals(p.getSettlementId(), settlementId)) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public Set<Integer> findPendingShareIds(int householdId) {
        Set<Integer> ids = new HashSet<>();
        for (Payment p : db.payments) {
            ExpenseShare s = db.share(p.getShareId());
            Expense e = s == null ? null : db.expense(s.getExpenseId());
            if (e != null && e.getHouseholdId() == householdId && p.getStatus() == PaymentStatus.PENDING) {
                ids.add(p.getShareId());
            }
        }
        return ids;
    }
}
