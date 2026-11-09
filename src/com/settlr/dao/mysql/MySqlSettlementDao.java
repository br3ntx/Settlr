package com.settlr.dao.mysql;

import com.settlr.dao.SettlementDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.PaymentStatus;
import com.settlr.model.Settlement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** The settlements table in MySQL (one row per "settle up"). */
public class MySqlSettlementDao extends AbstractDao<Settlement> implements SettlementDao {

    private static final String COLUMNS = "settlement_id, household_id, from_user, to_user, amount, "
            + "note, created_at, status, responded_at";

    @Override
    public Settlement findById(int id) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM settlements WHERE settlement_id = ?",
                "Could not load the settle up.", id);
    }

    @Override
    public List<Settlement> findPendingTo(int userId, int householdId) throws DataAccessException {
        return queryList("SELECT " + COLUMNS + " FROM settlements WHERE to_user = ? AND household_id = ? "
                        + "AND status = 'PENDING' ORDER BY created_at, settlement_id",
                "Could not load the payments waiting for you.", userId, householdId);
    }

    @Override
    public List<Settlement> findPendingInHousehold(int householdId) throws DataAccessException {
        return queryList("SELECT " + COLUMNS + " FROM settlements WHERE household_id = ? "
                        + "AND status = 'PENDING' ORDER BY created_at, settlement_id",
                "Could not load the pending settle ups.", householdId);
    }

    @Override
    public int insert(Settlement s) throws DataAccessException {
        return withConnection("Could not save the settle up.", c -> insert(c, s));
    }

    @Override
    public int insert(Connection c, Settlement s) throws DataAccessException {
        int id = insert(c, "INSERT INTO settlements (household_id, from_user, to_user, amount, note, created_at, "
                        + "status, responded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "Could not save the settle up.",
                s.getHouseholdId(), s.getFromUser(), s.getToUser(), s.getAmount(), s.getNote(), s.getCreatedAt(),
                s.getStatus(), s.getRespondedAt());
        s.setId(id);
        return id;
    }

    @Override
    public void update(Settlement s) throws DataAccessException {
        withConnection("Could not save the settle up.", c -> {
            update(c, s);
            return null;
        });
    }

    /** Saves the status and response time (the only parts that change after creation). */
    @Override
    public void update(Connection c, Settlement s) throws DataAccessException {
        update(c, "UPDATE settlements SET status = ?, responded_at = ? WHERE settlement_id = ?",
                "Could not save the settle up.", s.getStatus(), s.getRespondedAt(), s.getId());
    }

    /** Settle ups are kept as history; this fails if payments still point to it. */
    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM settlements WHERE settlement_id = ?", "Could not delete the settle up.", id);
    }

    @Override
    protected Settlement mapRow(ResultSet rs) throws SQLException {
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
