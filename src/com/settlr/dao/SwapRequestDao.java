package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.SwapRequest;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

/** The swap_requests table. Reads fill in the task title, due date and both names. */
public interface SwapRequestDao extends Dao<SwapRequest> {

    /** Every swap asked of this user, newest first. */
    List<SwapRequest> findIncoming(int userId) throws DataAccessException;

    /** Every swap this user asked for, newest first. */
    List<SwapRequest> findOutgoing(int userId) throws DataAccessException;

    /** The PENDING swap of a turn, or null (there is at most one). */
    SwapRequest findPendingForAssignment(int assignmentId) throws DataAccessException;

    /** PENDING swaps whose turn is due before the given time (catch-up declines these). */
    List<SwapRequest> findPendingPastDue(LocalDateTime now) throws DataAccessException;

    void update(Connection c, SwapRequest s) throws DataAccessException;

    /** Deletes the swaps of a task's PENDING turns due after the given time. */
    void deleteByTaskAfter(Connection c, int taskId, LocalDateTime after) throws DataAccessException;
}
