package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.RecurringBill;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

/**
 * recurring_bills and recurring_bill_members together: saving a bill also saves who it's split
 * among, in one transaction.
 */
public interface RecurringBillDao extends Dao<RecurringBill> {

    /** Every bill of one household, running or paused, oldest first. */
    List<RecurringBill> findByHousehold(int householdId) throws DataAccessException;

    /** Takes someone off every bill of a household (when they leave or are removed). */
    void removeMemberFromBills(int householdId, int userId) throws DataAccessException;

    /** Every running bill of every household (for the startup catch-up). */
    List<RecurringBill> findActive() throws DataAccessException;

    /** Records the due date of the last expense made from the bill, inside the caller's transaction. */
    void updateLastGenerated(Connection c, int billId, LocalDate dueDate) throws DataAccessException;
}
