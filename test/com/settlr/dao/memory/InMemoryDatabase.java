package com.settlr.dao.memory;

import com.settlr.model.ActivityLog;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Household;
import com.settlr.model.Membership;
import com.settlr.model.Payment;
import com.settlr.model.RecurringBill;
import com.settlr.model.Settlement;
import com.settlr.model.SwapRequest;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The "tables" of the in-memory DAOs: plain lists, shared by every DAO of one InMemoryDaoFactory. */
class InMemoryDatabase {

    final List<User> users = new ArrayList<>();
    final List<Membership> memberships = new ArrayList<>();
    final List<Expense> expenses = new ArrayList<>();
    final List<ExpenseShare> shares = new ArrayList<>();
    final List<Payment> payments = new ArrayList<>();
    final List<Household> households = new ArrayList<>();
    final List<Task> tasks = new ArrayList<>();
    final List<TaskAssignment> assignments = new ArrayList<>();
    final List<SwapRequest> swaps = new ArrayList<>();
    final List<ActivityLog> activity = new ArrayList<>();
    final List<RecurringBill> bills = new ArrayList<>();
    final List<Settlement> settlements = new ArrayList<>();
    final Map<Integer, Map<String, String[]>> owned = new HashMap<>(); // household id -> item code -> {pet name, hat, glasses}
    final Map<Integer, String> activePets = new HashMap<>();           // household id -> shown pet
    final List<int[]> xpTransactions = new ArrayList<>();              // {household id, user id, amount}

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

    /** The task with this id, or null. */
    Task task(int id) {
        for (Task t : tasks) {
            if (t.getId() == id) {
                return t;
            }
        }
        return null;
    }

    /** The stored turn with this id, or null. */
    TaskAssignment assignment(int id) {
        for (TaskAssignment a : assignments) {
            if (a.getId() == id) {
                return a;
            }
        }
        return null;
    }

    /** The user with this id, or null. */
    User user(int id) {
        for (User u : users) {
            if (u.getId() == id) {
                return u;
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
