package com.settlr.dao;

import com.settlr.dao.mysql.MySqlDaoFactory;

/**
 * Hands out the DAOs (Abstract Factory pattern).
 *
 * The services never write "new MySqlTaskDao()". They ask the factory for "a TaskDao" and get
 * whichever family of DAOs is plugged in:
 *   - MySqlDaoFactory (the app): every DAO reads and writes the MySQL database
 *   - InMemoryDaoFactory (the tests, in test/): DAOs that keep data in plain Java lists,
 *     so the tests can check the services without MySQL running
 *
 *     private final TaskDao taskDao = DaoFactory.get().tasks();
 *
 * Because every DAO comes from the same factory, the app can never mix a MySQL TaskDao with
 * an in-memory UserDao by mistake.
 */
public abstract class DaoFactory {

    private static DaoFactory current = new MySqlDaoFactory();

    /** The factory in use (MySQL, unless a test switched it). */
    public static DaoFactory get() {
        return current;
    }

    /** Switches every service created after this call to another family of DAOs (used by tests). */
    public static void use(DaoFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory is required.");
        }
        current = factory;
    }

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
