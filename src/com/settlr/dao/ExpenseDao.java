package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

/** The expenses table. delete() also removes the expense's shares and their payments. */
public interface ExpenseDao extends Dao<Expense> {

    /** Saves a new expense inside the caller's transaction and returns its id. */
    int insert(Connection c, Expense expense) throws DataAccessException;

    /** Newest first. Pass null to get every category. */
    List<Expense> findByHousehold(int householdId, ExpenseCategory filterOrNull) throws DataAccessException;

    /** True if this recurring bill already made an expense with this due date. */
    boolean existsForBill(int billId, LocalDate dueDate) throws DataAccessException;
}
