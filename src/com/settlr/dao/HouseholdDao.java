package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Household;

/** The households table. */
public interface HouseholdDao extends Dao<Household> {

    /** The household with this invite code, or null if the code doesn't exist. */
    Household findByInviteCode(String code) throws DataAccessException;
}
