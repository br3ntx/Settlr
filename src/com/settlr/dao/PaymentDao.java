package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Payment;

import java.sql.Connection;
import java.util.List;
import java.util.Set;

/** The payments table. Saving a payment doesn't confirm it; PaymentService decides that. */
public interface PaymentDao extends Dao<Payment> {

    int insert(Connection c, Payment p) throws DataAccessException;

    void update(Connection c, Payment p) throws DataAccessException;

    /** PENDING single-share payments receiverId still has to confirm, oldest first (settle ups left out). */
    List<Payment> findWaitingFor(int receiverId) throws DataAccessException;

    /** The PENDING payment of this share, or null. */
    Payment findPendingForShare(int shareId) throws DataAccessException;

    /** True if any share of this expense has a CONFIRMED payment. */
    boolean existsConfirmedForExpense(int expenseId) throws DataAccessException;

    /** Every payment that belongs to one settle up. */
    List<Payment> findBySettlement(int settlementId) throws DataAccessException;

    /** Ids of the household's shares that have a PENDING payment. */
    Set<Integer> findPendingShareIds(int householdId) throws DataAccessException;
}
