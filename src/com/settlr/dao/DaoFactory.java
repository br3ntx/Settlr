package com.settlr.dao;

import com.settlr.dao.mysql.MySqlDaoFactory;
import com.settlr.util.TransactionRunner;

/**
 * Hands out the DAOs (Abstract Factory pattern).
 *
 * The services never write "new MySqlTaskDao()". They ask a factory for "a TaskDao" and get
 * whichever family of DAOs is plugged in:
 *   - MySqlDaoFactory (the app): every DAO reads and writes the MySQL database
 *   - InMemoryDaoFactory (the tests, in test/): DAOs that keep data in plain Java lists,
 *     so the tests can check the services without MySQL running
 *
 *     public TaskService(DaoFactory daos, Session session) {   // tests pass an InMemoryDaoFactory
 *         this.taskDao = daos.tasks();
 *     }
 *     public TaskService() { this(DaoFactory.get(), Session.getInstance()); }   // the app
 *
 * Because every DAO comes from the same factory, the app can never mix a MySQL TaskDao with
 * an in-memory UserDao by mistake.
 */
public abstract class DaoFactory {

    /** The factory the app uses. It never changes: tests pass their own factory to each service instead. */
    private static final DaoFactory DEFAULT = new MySqlDaoFactory();

    /** The MySQL factory, used by every service made with its no-argument constructor. */
    public static DaoFactory get() {
        return DEFAULT;
    }

    /** How this family of DAOs runs a transaction (real MySQL transactions, or none for in-memory lists). */
    public abstract TransactionRunner transactions();

    public abstract UserDao users();

    public abstract HouseholdDao households();

    public abstract MembershipDao memberships();

    public abstract TaskDao tasks();

    public abstract TaskAssignmentDao assignments();

    public abstract SwapRequestDao swaps();

    public abstract ExpenseDao expenses();

    public abstract ExpenseShareDao shares();

    public abstract PaymentDao payments();

    public abstract SettlementDao settlements();

    public abstract RecurringBillDao recurringBills();

    public abstract ActivityLogDao activityLog();

    public abstract ShopDao shop();
}
