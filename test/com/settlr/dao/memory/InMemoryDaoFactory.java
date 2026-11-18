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
import com.settlr.exception.SettlrException;
import com.settlr.util.Transaction;
import com.settlr.util.TransactionRunner;

// DESIGN PATTERN: Abstract Factory (Concrete Factory) - makes the in-memory DAOs the tests use, so they run without MySQL.
/**
 * The second family of DAOs (Abstract Factory): everything is kept in Java lists instead of
 * MySQL, so tests can check the services without a database.
 *
 *     InMemoryDaoFactory db = new InMemoryDaoFactory();
 *     db.users().insert(new User(...));
 *     TaskService tasks = new TaskService(db, Session.getInstance());   // uses the lists
 *
 * Every DAO has an in-memory version, so any service can be tested this way.
 */
public class InMemoryDaoFactory extends DaoFactory {

    private final InMemoryDatabase db = new InMemoryDatabase();

    @Override public UserDao users() { return new InMemoryUserDao(db); }

    @Override public MembershipDao memberships() { return new InMemoryMembershipDao(db); }

    @Override public ExpenseDao expenses() { return new InMemoryExpenseDao(db); }

    @Override public ExpenseShareDao shares() { return new InMemoryExpenseShareDao(db); }

    @Override public PaymentDao payments() { return new InMemoryPaymentDao(db); }

    @Override public HouseholdDao households() { return new InMemoryHouseholdDao(db); }

    @Override public TaskDao tasks() { return new InMemoryTaskDao(db); }

    @Override public TaskAssignmentDao assignments() { return new InMemoryTaskAssignmentDao(db); }

    @Override public SwapRequestDao swaps() { return new InMemorySwapRequestDao(db); }

    @Override public RecurringBillDao recurringBills() { return new InMemoryRecurringBillDao(db); }

    @Override public ActivityLogDao activityLog() { return new InMemoryActivityLogDao(db); }

    @Override public ShopDao shop() { return new InMemoryShopDao(db); }

    @Override public SettlementDao settlements() { return new InMemorySettlementDao(db); }

    /**
     * Lists can't roll back, so "transactions" here just run the work. Tests check what
     * the services save, not what happens when the database fails halfway.
     */
    @Override
    public TransactionRunner transactions() {
        return new TransactionRunner() {
            @Override
            public <T> T get(String failMessage, Transaction.WorkWithResult<T> work) throws SettlrException {
                return work.run(null); // in-memory DAOs never use the Connection
            }
        };
    }
}
