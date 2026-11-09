package com.settlr.dao.memory;

import com.settlr.dao.ExpenseShareDao;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseShare;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Expense shares kept in a list. */
class InMemoryExpenseShareDao implements ExpenseShareDao {

    private final InMemoryDatabase db;

    InMemoryExpenseShareDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public ExpenseShare findById(int id) {
        return db.share(id);
    }

    @Override
    public int insert(ExpenseShare share) {
        share.setId(db.newId());
        db.shares.add(share);
        return share.getId();
    }

    @Override
    public int insert(Connection c, ExpenseShare share) {
        return insert(share);
    }

    @Override
    public void update(ExpenseShare share) {
        delete(share.getId());
        db.shares.add(share);
    }

    @Override
    public void update(Connection c, ExpenseShare share) {
        update(share);
    }

    @Override
    public void delete(int id) {
        db.shares.removeIf(s -> s.getId() == id);
    }

    @Override
    public List<ExpenseShare> findUnsettled(int debtorId, int creditorId) {
        List<ExpenseShare> result = new ArrayList<>();
        for (ExpenseShare s : db.shares) {
            Expense e = db.expense(s.getExpenseId());
            if (s.getUserId() == debtorId && e != null && e.getPaidBy() == creditorId && s.getSettledAt() == null) {
                result.add(s);
            }
        }
        result.sort(Comparator.comparing((ExpenseShare s) -> db.expense(s.getExpenseId()).getDueDate())
                .thenComparingInt(ExpenseShare::getId));
        return result;
    }

    @Override
    public List<ExpenseShare> findUnsettledPastDue(int userId, LocalDate today) {
        List<ExpenseShare> result = new ArrayList<>();
        for (ExpenseShare s : db.shares) {
            Expense e = db.expense(s.getExpenseId());
            if (s.getUserId() == userId && s.getSettledAt() == null && e != null && e.getDueDate().isBefore(today)) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public List<ExpenseShare> findByHousehold(int householdId) {
        List<ExpenseShare> result = new ArrayList<>();
        for (ExpenseShare s : db.shares) {
            Expense e = db.expense(s.getExpenseId());
            if (e != null && e.getHouseholdId() == householdId) {
                result.add(s);
            }
        }
        return result;
    }
}
