package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.ExpenseShare;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

/** The expense_shares table: one person's part of an expense. No settled date = unpaid. */
public interface ExpenseShareDao extends Dao<ExpenseShare> {

    int insert(Connection c, ExpenseShare share) throws DataAccessException;

    void update(Connection c, ExpenseShare share) throws DataAccessException;

    /** Unpaid shares debtorId owes for expenses creditorId paid, oldest due date first. */
    List<ExpenseShare> findUnsettled(int debtorId, int creditorId) throws DataAccessException;

    /** Unpaid shares of this user whose expense was due before today. */
    List<ExpenseShare> findUnsettledPastDue(int userId, LocalDate today) throws DataAccessException;

    /** Every share (paid or not) of every expense in the household. */
    List<ExpenseShare> findByHousehold(int householdId) throws DataAccessException;
}
