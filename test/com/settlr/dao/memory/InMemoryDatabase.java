package com.settlr.dao.memory;

import com.settlr.model.Expense;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Membership;
import com.settlr.model.Payment;
import com.settlr.model.User;

import java.util.ArrayList;
import java.util.List;

/** The "tables" of the in-memory DAOs: plain lists, shared by every DAO of one InMemoryDaoFactory. */
class InMemoryDatabase {

    final List<User> users = new ArrayList<>();
    final List<Membership> memberships = new ArrayList<>();
    final List<Expense> expenses = new ArrayList<>();
    final List<ExpenseShare> shares = new ArrayList<>();
    final List<Payment> payments = new ArrayList<>();

    private int nextId = 1;

    /** Hands out ids like AUTO_INCREMENT does. */
    int newId() {
        return nextId++;
    }

    Expense expense(int id) {
        for (Expense e : expenses) {
            if (e.getId() == id) {
                return e;
            }
        }
        return null;
    }

    ExpenseShare share(int id) {
        for (ExpenseShare s : shares) {
            if (s.getId() == id) {
                return s;
            }
        }
        return null;
    }
}
