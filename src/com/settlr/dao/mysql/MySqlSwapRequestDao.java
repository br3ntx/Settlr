package com.settlr.dao.mysql;

import com.settlr.dao.SwapRequestDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** The swap_requests table in MySQL. Reads join the turn, the task and both users. */
public class MySqlSwapRequestDao extends AbstractDao<SwapRequest> implements SwapRequestDao {

    private static final String SELECT =
            "SELECT sr.swap_id, sr.assignment_id, sr.from_user, sr.to_user, sr.status, "
          + "sr.created_at, sr.responded_at, t.title, ta.due_at, "
          + "fu.display_name AS from_name, tu.display_name AS to_name "
          + "FROM swap_requests sr "
          + "JOIN task_assignments ta ON ta.assignment_id = sr.assignment_id "
          + "JOIN tasks t ON t.task_id = ta.task_id "
          + "JOIN users fu ON fu.user_id = sr.from_user "
          + "JOIN users tu ON tu.user_id = sr.to_user ";

    @Override
    public SwapRequest findById(int id) throws DataAccessException {
        return queryOne(SELECT + "WHERE sr.swap_id = ?", "Could not load the swap request.", id);
    }

    @Override
    public List<SwapRequest> findIncoming(int userId) throws DataAccessException {
        return queryList(SELECT + "WHERE sr.to_user = ? ORDER BY sr.created_at DESC",
                "Could not load the swap requests.", userId);
    }

    @Override
    public List<SwapRequest> findOutgoing(int userId) throws DataAccessException {
        return queryList(SELECT + "WHERE sr.from_user = ? ORDER BY sr.created_at DESC",
                "Could not load the swap requests.", userId);
    }

    @Override
    public SwapRequest findPendingForAssignment(int assignmentId) throws DataAccessException {
        return queryOne(SELECT + "WHERE sr.assignment_id = ? AND sr.status = 'PENDING'",
                "Could not load the swap request.", assignmentId);
    }

    @Override
    public List<SwapRequest> findPendingPastDue(LocalDateTime now) throws DataAccessException {
        return queryList(SELECT + "WHERE sr.status = 'PENDING' AND ta.due_at < ? ORDER BY ta.due_at",
                "Could not load the swap requests.", now);
    }

    @Override
    public int insert(SwapRequest s) throws DataAccessException {
        return insert("INSERT INTO swap_requests (assignment_id, from_user, to_user, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                "Could not save the swap request.",
                s.getAssignmentId(), s.getFromUser(), s.getToUser(), s.getStatus(), s.getCreatedAt());
    }

    @Override
    public void update(SwapRequest s) throws DataAccessException {
        withConnection("Could not update the swap request.", c -> {
            update(c, s);
            return null;
        });
    }

    @Override
    public void update(Connection c, SwapRequest s) throws DataAccessException {
        update(c, "UPDATE swap_requests SET status = ?, responded_at = ? WHERE swap_id = ?",
                "Could not update the swap request.", s.getStatus(), s.getRespondedAt(), s.getId());
    }

    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM swap_requests WHERE swap_id = ?", "Could not delete the swap request.", id);
    }

    /** Needed first because the schema has no cascade from task_assignments to swap_requests. */
    @Override
    public void deleteByTaskAfter(Connection c, int taskId, LocalDateTime after) throws DataAccessException {
        update(c, "DELETE sr FROM swap_requests sr "
                        + "JOIN task_assignments ta ON ta.assignment_id = sr.assignment_id "
                        + "WHERE ta.task_id = ? AND ta.status = 'PENDING' AND ta.due_at > ?",
                "Could not remove the swap requests.", taskId, after);
    }

    @Override
    protected SwapRequest mapRow(ResultSet rs) throws SQLException {
        SwapRequest s = new SwapRequest(
                rs.getInt("assignment_id"),
                rs.getInt("from_user"),
                rs.getInt("to_user"));
        s.setId(rs.getInt("swap_id"));
        s.setStatus(SwapStatus.valueOf(rs.getString("status")));
        s.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        s.setRespondedAt(rs.getObject("responded_at", LocalDateTime.class));
        s.setTaskTitle(rs.getString("title"));
        s.setAssignmentDueAt(rs.getObject("due_at", LocalDateTime.class));
        s.setFromUserName(rs.getString("from_name"));
        s.setToUserName(rs.getString("to_name"));
        return s;
    }
}
