package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.User;

/** The users table. */
public interface UserDao extends Dao<User> {

    /**
     * The user with this username, or null if there isn't one.
     * Not case-sensitive ("Ana" finds "ana").
     */
    User findByUsername(String username) throws DataAccessException;
}
