package com.settlr.dao;

import com.settlr.exception.DataAccessException;

/**
 * The four basic operations every DAO supports, for one kind of record T
 * (for example Dao<Task> or Dao<Expense>).
 *
 * Generics let one interface describe all DAOs, and screens and services can
 * rely on these methods existing without knowing the SQL behind them (abstraction).
 * Each DAO adds its own extra finders, like TaskAssignmentDao.findBetween(...).
 */
public interface Dao<T> {

    /** Returns the record with this id, or null if there isn't one. */
    T findById(int id) throws DataAccessException;

    /** Saves a new record and returns the id the database gave it. */
    int insert(T item) throws DataAccessException;

    /** Saves changes to an existing record (matched by its id). */
    void update(T item) throws DataAccessException;

    /** Deletes the record with this id. */
    void delete(int id) throws DataAccessException;
}
