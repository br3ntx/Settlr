package com.settlr.dao.mysql;

import com.settlr.dao.MembershipDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.Membership;
import com.settlr.model.Role;
import com.settlr.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** The household_members table in MySQL. */
public class MySqlMembershipDao extends AbstractDao<Membership> implements MembershipDao {

    private static final String COLUMNS = "household_id, user_id, role, joined_at, is_active";

    @Override
    public List<User> findActiveMembers(int householdId) throws DataAccessException {
        String sql = "SELECT " + MySqlUserDao.columns("u") + " "
                + "FROM household_members hm "
                + "JOIN users u ON u.user_id = hm.user_id "
                + "WHERE hm.household_id = ? AND hm.is_active = TRUE "
                + "ORDER BY hm.joined_at, u.user_id";
        return query(sql, MySqlUserDao::read, "Could not load the household members.", householdId);
    }

    @Override
    public Membership findActiveMembershipOf(int userId) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM household_members "
                        + "WHERE user_id = ? AND is_active = TRUE ORDER BY joined_at DESC LIMIT 1",
                "Could not load your household membership.", userId);
    }

    @Override
    public Membership find(int householdId, int userId) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM household_members WHERE household_id = ? AND user_id = ?",
                "Could not load the membership.", householdId, userId);
    }

    /** If they were in this household before and got removed, this re-activates them with the new role. */
    @Override
    public void insert(Membership m) throws DataAccessException {
        update("INSERT INTO household_members (household_id, user_id, role, joined_at, is_active) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), is_active = VALUES(is_active)",
                "Could not add the member to the household.",
                m.getHouseholdId(), m.getUserId(), m.getRole(), m.getJoinedAt(), m.isActive());
    }

    @Override
    public List<Membership> findActiveMemberships(int householdId) throws DataAccessException {
        return queryList("SELECT " + COLUMNS + " FROM household_members "
                        + "WHERE household_id = ? AND is_active = TRUE ORDER BY joined_at, user_id",
                "Could not load the household members.", householdId);
    }

    @Override
    public void updateRole(int householdId, int userId, Role role) throws DataAccessException {
        update("UPDATE household_members SET role = ? WHERE household_id = ? AND user_id = ?",
                "Could not change the member's role.", role, householdId, userId);
    }

    @Override
    public void deactivate(int householdId, int userId) throws DataAccessException {
        update("UPDATE household_members SET is_active = FALSE WHERE household_id = ? AND user_id = ?",
                "Could not remove the member.", householdId, userId);
    }

    @Override
    protected Membership mapRow(ResultSet rs) throws SQLException {
        return new Membership(
                rs.getInt("household_id"),
                rs.getInt("user_id"),
                Role.valueOf(rs.getString("role")),
                rs.getObject("joined_at", LocalDateTime.class),
                rs.getBoolean("is_active"));
    }
}
