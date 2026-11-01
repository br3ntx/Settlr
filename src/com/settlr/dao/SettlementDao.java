package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.PaymentStatus;
import com.settlr.model.Settlement;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** All SQL for the settlements table (one row per "settle up"). */
public class SettlementDao implements Dao<Settlement> {

    private static final String COLUMNS = "settlement_id, household_id, from_user, to_user, amount, "
            + "note, created_at, status, responded_at";

    @Override
    public Settlement findById(int id) throws DataAccessException {
        List<Settlement> found = query("SELECT " + COLUMNS + " FROM settlements WHERE settlement_id = ?",
                "Could not load the settle up.", id);
        return found.isEmpty() ? null : found.get(0);
    }

    /** PENDING settle ups in this household that userId has to confirm or reject, oldest first. */
    public List<Settlement> findPendingTo(int userId, int householdId) throws DataAccessException {
        return query("SELECT " + COLUMNS + " FROM settlements WHERE to_user = ? AND household_id = ? "
                        + "AND status = 'PENDING' ORDER BY created_at, settlement_id",
                "Could not load the payments waiting for you.", userId, householdId);
    }

    /** Every PENDING settle up in this household (each screen picks out the ones that involve the user). */
    public List<Settlement> findPendingInHousehold(int householdId) throws DataAccessException {
        return query("SELECT " + COLUMNS + " FROM settlements WHERE household_id = ? "
                        + "AND status = 'PENDING' ORDER BY created_at, settlement_id",
                "Could not load the pending settle ups.", householdId);
    }

    @Override
    public int insert(Settlement s) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection()) {
            return insert(con, s);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the settle up.", ex);
        }
    }

    /** Saves a new settle up using a connection the caller controls (for transactions). */
    public int insert(Connection con, Settlement s) throws DataAccessException {
        String sql = "INSERT INTO settlements (household_id, from_user, to_user, amount, note, created_at, "
                + "status, responded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getHouseholdId());
            ps.setInt(2, s.getFromUser());
            ps.setInt(3, s.getToUser());
            ps.setBigDecimal(4, s.getAmount());
            ps.setString(5, s.getNote());
            ps.setObject(6, s.getCreatedAt());
            ps.setString(7, s.getStatus().name());
            setNullableTime(ps, 8, s.getRespondedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    s.setId(id);
                    return id;
                }
            }
            throw new SQLException("No generated id was returned.");
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the settle up.", ex);
        }
    }

    @Override
    public void update(Settlement s) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection()) {
            update(con, s);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the settle up.", ex);
        }
    }

    /** Saves the status and response time (the only parts that change after creation). */
    public void update(Connection con, Settlement s) throws DataAccessException {
        String sql = "UPDATE settlements SET status = ?, responded_at = ? WHERE settlement_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, s.getStatus().name());
            setNullableTime(ps, 2, s.getRespondedAt());
            ps.setInt(3, s.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the settle up.", ex);
        }
    }

    /** Settle ups are kept as history; this exists to complete Dao<T> (fails if payments still point to it). */
    @Override
    public void delete(int id) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM settlements WHERE settlement_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not delete the settle up.", ex);
        }
    }

    // ---------- helpers ----------

    private List<Settlement> query(String sql, String errorMessage, int... params) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setInt(i + 1, params[i]);
            }
            List<Settlement> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw new DataAccessException(errorMessage, ex);
        }
    }

    private static void setNullableTime(PreparedStatement ps, int index, LocalDateTime time) throws SQLException {
        if (time == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setObject(index, time);
        }
    }

    private Settlement map(ResultSet rs) throws SQLException {
        return new Settlement(
                rs.getInt("settlement_id"),
                rs.getInt("household_id"),
                rs.getInt("from_user"),
                rs.getInt("to_user"),
                rs.getBigDecimal("amount"),
                rs.getString("note"),
                rs.getObject("created_at", LocalDateTime.class),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getObject("responded_at", LocalDateTime.class));
    }
}
