package com.settlr.dao.memory;

import com.settlr.dao.ActivityLogDao;
import com.settlr.dao.DaoFactory;
import com.settlr.dao.ExpenseDao;
import com.settlr.dao.ExpenseShareDao;
import com.settlr.dao.HouseholdDao;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.PaymentDao;
import com.settlr.dao.RecurringBillDao;
import com.settlr.dao.SettlementDao;
import com.settlr.dao.ShopDao;
import com.settlr.dao.SwapRequestDao;
import com.settlr.dao.TaskAssignmentDao;
import com.settlr.dao.TaskDao;
import com.settlr.dao.UserDao;

/**
 * The second family of DAOs (Abstract Factory): everything is kept in Java lists instead of
 * MySQL, so tests can check the services without a database.
 *
 *     InMemoryDaoFactory db = new InMemoryDaoFactory();
 *     DaoFactory.use(db);              // services made after this use the lists
 *     db.users().insert(new User(...));
 *
 * Only the DAOs the tests need are filled in so far. Asking for another one fails with a
 * clear message; add it here when a test needs it.
 */
public class InMemoryDaoFactory extends DaoFactory {

    private final InMemoryDatabase db = new InMemoryDatabase();

    @Override public UserDao users() { return new InMemoryUserDao(db); }

    @Override public MembershipDao memberships() { return new InMemoryMembershipDao(db); }

    @Override public ExpenseDao expenses() { return new InMemoryExpenseDao(db); }

    @Override public ExpenseShareDao shares() { return new InMemoryExpenseShareDao(db); }

    @Override public PaymentDao payments() { return new InMemoryPaymentDao(db); }

    @Override public HouseholdDao households() { throw notYet("HouseholdDao"); }

    @Override public TaskDao tasks() { throw notYet("TaskDao"); }

    @Override public TaskAssignmentDao assignments() { throw notYet("TaskAssignmentDao"); }

    @Override public SwapRequestDao swaps() { throw notYet("SwapRequestDao"); }

    @Override public SettlementDao settlements() { throw notYet("SettlementDao"); }

    @Override public RecurringBillDao recurringBills() { throw notYet("RecurringBillDao"); }

    @Override public ActivityLogDao activityLog() { throw notYet("ActivityLogDao"); }

    @Override public ShopDao shop() { throw notYet("ShopDao"); }

    private static UnsupportedOperationException notYet(String dao) {
        return new UnsupportedOperationException(dao + " has no in-memory version yet. Add one to InMemoryDaoFactory.");
    }
}
