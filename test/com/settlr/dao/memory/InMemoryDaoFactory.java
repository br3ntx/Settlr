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

import java.lang.reflect.Proxy;

/**
 * The second family of DAOs (Abstract Factory): everything is kept in Java lists instead of
 * MySQL, so tests can check the services without a database.
 *
 *     InMemoryDaoFactory db = new InMemoryDaoFactory();
 *     db.users().insert(new User(...));
 *     TaskService tasks = new TaskService(db, Session.getInstance());   // uses the lists
 *
 * Every DAO the tests need is filled in. The few without an in-memory version yet
 * (settlements) fail with a clear message when a method is called; add one when a test needs it.
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

    @Override public SettlementDao settlements() { return notYet(SettlementDao.class); }

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

    /**
     * A stand-in for a DAO with no in-memory version yet. Services can still be created
     * (they ask for every DAO in their constructor); only calling one of its methods fails,
     * with a message that says what to add.
     */
    @SuppressWarnings("unchecked")
    private static <T> T notYet(Class<T> dao) {
        return (T) Proxy.newProxyInstance(dao.getClassLoader(), new Class<?>[] {dao}, (proxy, method, args) -> {
            switch (method.getName()) { // the methods every object has
                case "toString": return dao.getSimpleName() + " (not in memory)";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals":   return proxy == args[0];
                default: break;
            }
            throw new UnsupportedOperationException(dao.getSimpleName() + "." + method.getName()
                    + " has no in-memory version yet. Add one to InMemoryDaoFactory.");
        });
    }
}
