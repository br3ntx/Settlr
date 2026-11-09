package com.settlr.dao.memory;

import com.settlr.dao.ExpenseDao;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Expenses kept in a list. The Connection parameters are ignored: there's no database. */
class InMemoryExpenseDao implements ExpenseDao {

    private final InMemoryDatabase db;

    InMemoryExpenseDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Expense findById(int id) {
        return db.expense(id);
    }

    @Override
    public int insert(Expense expense) {
        expense.setId(db.newId());
        db.expenses.add(expense);
        return expense.getId();
    }

    @Override
    public int insert(Connection c, Expense expense) {
        return insert(expense);
    }

    @Override
    public void update(Expense expense) {
        delete(expense.getId());
        db.expenses.add(expense);
    }

    /** Like ON DELETE CASCADE: the expense's shares go too. */
    @Override
    public void delete(int id) {
        db.expenses.removeIf(e -> e.getId() == id);
        db.shares.removeIf(s -> s.getExpenseId() == id);
    }

    @Override
    public List<Expense> findByHousehold(int householdId, ExpenseCategory filterOrNull) {
        List<Expense> result = new ArrayList<>();
        for (int i = db.expenses.size() - 1; i >= 0; i--) { // newest first
            Expense e = db.expenses.get(i);
            if (e.getHouseholdId() == householdId && (filterOrNull == null || e.getCategory() == filterOrNull)) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public boolean existsForBill(int billId, LocalDate dueDate) {
        for (Expense e : db.expenses) {
            if (Objects.equals(e.getBillId(), billId) && e.getDueDate().equals(dueDate)) {
                return true;
            }
        }
        return false;
    }
}
