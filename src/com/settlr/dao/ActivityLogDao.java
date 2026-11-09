package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.ActivityLog;

import java.util.List;

/** The activity_log table (what happened in a household, newest first). */
public interface ActivityLogDao extends Dao<ActivityLog> {

    /** The newest events of a household, newest first, at most limit of them. */
    List<ActivityLog> findRecent(int householdId, int limit) throws DataAccessException;
}
