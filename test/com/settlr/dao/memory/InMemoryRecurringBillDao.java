package com.settlr.dao.memory;

import com.settlr.dao.RecurringBillDao;
import com.settlr.model.RecurringBill;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Monthly bills kept in a list. Enough for the start-up catch-up, which only reads active bills. */
class InMemoryRecurringBillDao implements RecurringBillDao {

    private final InMemoryDatabase db;

    InMemoryRecurringBillDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public RecurringBill findById(int id) {
        for (RecurringBill b : db.bills) {
            if (b.getId() == id) {
                return b;
            }
        }
        return null;
    }

    @Override
    public List<RecurringBill> findByHousehold(int householdId) {
        List<RecurringBill> result = new ArrayList<>();
        for (RecurringBill b : db.bills) {
            if (b.getHouseholdId() == householdId) {
                result.add(b);
            }
        }
        return result;
    }

    @Override
    public List<RecurringBill> findActive() {
        List<RecurringBill> result = new ArrayList<>();
        for (RecurringBill b : db.bills) {
            if (b.isActive()) {
                result.add(b);
            }
        }
        return result;
    }

    @Override
    public void removeMemberFromBills(int householdId, int userId) {
        for (RecurringBill b : findByHousehold(householdId)) {
            b.getMemberIds().remove(Integer.valueOf(userId));
        }
    }

    @Override
    public void updateLastGenerated(Connection c, int billId, LocalDate dueDate) {
        throw new UnsupportedOperationException("Generating bills isn't supported in memory yet.");
    }

    @Override
    public int insert(RecurringBill b) {
        db.bills.add(b);
        return b.getId();
    }

    @Override
    public void update(RecurringBill b) {
        // the list holds the same object, so it's already changed
    }

    @Override
    public void delete(int id) {
        db.bills.removeIf(b -> b.getId() == id);
    }
}
