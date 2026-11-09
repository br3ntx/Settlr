package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Settlement;

import java.sql.Connection;
import java.util.List;

/** The settlements table (one row per "settle up"). */
public interface SettlementDao extends Dao<Settlement> {

    /** PENDING settle ups in this household that userId has to confirm, oldest first. */
    List<Settlement> findPendingTo(int userId, int householdId) throws DataAccessException;

    /** Every PENDING settle up in the household. */
    List<Settlement> findPendingInHousehold(int householdId) throws DataAccessException;

    int insert(Connection c, Settlement s) throws DataAccessException;

    void update(Connection c, Settlement s) throws DataAccessException;
}
