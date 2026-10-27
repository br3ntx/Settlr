package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Household;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Reads and writes the households table. */
public class HouseholdDao implements Dao<Household> {

    private static final String COLUMNS = "household_id, name, invite_code, created_by, hide_shame_ranks";

    @Override
    public Household findById(int id) throws DataAccessException {
        return findOne("SELECT " + COLUMNS + " FROM households WHERE household_id = ?", id,
                "Could not load the household.");
    }

    /** The household with this invite code, or null if the code doesn't exist. */
    public Household findByInviteCode(String code) throws DataAccessException {
        return findOne("SELECT " + COLUMNS + " FROM households WHERE invite_code = ?", code,
                "Could not look up that invite code.");
    }

    @Override
    public int insert(Household h) throws DataAccessException {
        String sql = "INSERT INTO households (name, invite_code, created_by, hide_shame_ranks) VALUES (?, ?, ?, ?)";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, h.getName());
            ps.setString(2, h.getInviteCode());
            ps.setInt(3, h.getCreatedBy());
            ps.setBoolean(4, h.isHideShameRanks());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                h.setId(id);
                return id;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create the household.", e);
        }
    }

    /** Saves the name, invite code and hide-shame-ranks setting. */
    @Override
    public void update(Household h) throws DataAccessException {
        String sql = "UPDATE households SET name = ?, invite_code = ?, hide_shame_ranks = ? WHERE household_id = ?";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, h.getName());
            ps.setString(2, h.getInviteCode());
            ps.setBoolean(3, h.isHideShameRanks());
            ps.setInt(4, h.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the household.", e);
        }
    }

    /** Only works for an empty household (the foreign keys block the rest). */
    @Override
    public void delete(int id) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM households WHERE household_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the household.", e);
        }
    }

    // ---------- helpers ----------

    private Household findOne(String sql, Object param, String errorMessage) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    private Household mapRow(ResultSet rs) throws SQLException {
        return new Household(
                rs.getInt("household_id"),
                rs.getString("name"),
                rs.getString("invite_code"),
                rs.getInt("created_by"),
                rs.getBoolean("hide_shame_ranks"));
    }
}
