package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the swap_requests table. Reads JOIN the assignment, task and
 * both users so SwapRequestsDialog can show titles and names directly.
 * No business rules live here.
 */
public class SwapRequestDao implements Dao<SwapRequest> {

    private static final String SELECT =
            "SELECT sr.swap_id, sr.assignment_id, sr.from_user, sr.to_user, sr.status, "
          + "sr.created_at, sr.responded_at, t.title, ta.due_at, "
          + "fu.display_name AS from_name, tu.display_name AS to_name "
          + "FROM swap_requests sr "
          + "JOIN task_assignments ta ON ta.assignment_id = sr.assignment_id "
          + "JOIN tasks t ON t.task_id = ta.task_id "
          + "JOIN users fu ON fu.user_id = sr.from_user "
          + "JOIN users tu ON tu.user_id = sr.to_user ";

    /** Returns null when no swap has this id. */
    @Override
    public SwapRequest findById(int id) throws DataAccessException {
        return findOne(SELECT + "WHERE sr.swap_id = ?", id);
    }

    /** Every swap asked of this user, newest first. The caller filters by status. */
    public List<SwapRequest> findIncoming(int userId) throws DataAccessException {
        return findMany(SELECT + "WHERE sr.to_user = ? ORDER BY sr.created_at DESC", userId);
    }

    /** Every swap this user asked for, newest first. */
    public List<SwapRequest> findOutgoing(int userId) throws DataAccessException {
        return findMany(SELECT + "WHERE sr.from_user = ? ORDER BY sr.created_at DESC", userId);
    }

    /** The PENDING swap of an assignment, or null (there is at most one). */
    public SwapRequest findPendingForAssignment(int assignmentId) throws DataAccessException {
        return findOne(SELECT + "WHERE sr.assignment_id = ? AND sr.status = 'PENDING'", assignmentId);
    }

    @Override
    public int insert(SwapRequest s) throws DataAccessException {
        String sql = "INSERT INTO swap_requests (assignment_id, from_user, to_user, status, created_at) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getAssignmentId());
            ps.setInt(2, s.getFromUser());
            ps.setInt(3, s.getToUser());
            ps.setString(4, s.getStatus().name());
            ps.setObject(5, s.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DataAccessException("Could not save the swap request.");
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the swap request.");
        }
    }

    @Override
    public void update(SwapRequest s) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            update(c, s);
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the swap request.");
        }
    }

    /** Updates status and response time inside the caller's transaction (accepting also changes an assignment). */
    public void update(Connection c, SwapRequest s) throws DataAccessException {
        String sql = "UPDATE swap_requests SET status = ?, responded_at = ? WHERE swap_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getStatus().name());
            ps.setObject(2, s.getRespondedAt());
            ps.setInt(3, s.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the swap request.");
        }
    }

    @Override
    public void delete(int id) throws DataAccessException {
        String sql = "DELETE FROM swap_requests WHERE swap_id = ?";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the swap request.");
        }
    }

    /**
     * Deletes the swaps of a task's PENDING assignments due after the given
     * time, inside the caller's transaction. Needed first because the schema
     * has no cascade from task_assignments to swap_requests.
     */
    public void deleteByTaskAfter(Connection c, int taskId, LocalDateTime after) throws DataAccessException {
        String sql = "DELETE sr FROM swap_requests sr "
                   + "JOIN task_assignments ta ON ta.assignment_id = sr.assignment_id "
                   + "WHERE ta.task_id = ? AND ta.status = 'PENDING' AND ta.due_at > ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.setObject(2, after);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not remove the swap requests.");
        }
    }

    // ---- helpers -------------------------------------------------------

    private SwapRequest findOne(String sql, int param) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the swap request.");
        }
    }

    private List<SwapRequest> findMany(String sql, int param) throws DataAccessException {
        List<SwapRequest> list = new ArrayList<>();
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the swap requests.");
        }
    }

    private SwapRequest mapRow(ResultSet rs) throws SQLException {
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
