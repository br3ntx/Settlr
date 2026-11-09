package com.settlr.dao.mysql;

import com.settlr.dao.HouseholdDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.Household;

import java.sql.ResultSet;
import java.sql.SQLException;

/** The households table in MySQL. */
public class MySqlHouseholdDao extends AbstractDao<Household> implements HouseholdDao {

    private static final String COLUMNS = "household_id, name, invite_code, created_by";

    @Override
    public Household findById(int id) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM households WHERE household_id = ?",
                "Could not load the household.", id);
    }

    @Override
    public Household findByInviteCode(String code) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM households WHERE invite_code = ?",
                "Could not look up that invite code.", code);
    }

    @Override
    public int insert(Household h) throws DataAccessException {
        int id = insert("INSERT INTO households (name, invite_code, created_by) VALUES (?, ?, ?)",
                "Could not create the household.", h.getName(), h.getInviteCode(), h.getCreatedBy());
        h.setId(id);
        return id;
    }

    /** Saves the household name and invite code. */
    @Override
    public void update(Household h) throws DataAccessException {
        update("UPDATE households SET name = ?, invite_code = ? WHERE household_id = ?",
                "Could not save the household.", h.getName(), h.getInviteCode(), h.getId());
    }

    /** Only works for an empty household (the foreign keys block the rest). */
    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM households WHERE household_id = ?", "Could not delete the household.", id);
    }

    @Override
    protected Household mapRow(ResultSet rs) throws SQLException {
        return new Household(
                rs.getInt("household_id"),
                rs.getString("name"),
                rs.getString("invite_code"),
                rs.getInt("created_by"));
    }
}
