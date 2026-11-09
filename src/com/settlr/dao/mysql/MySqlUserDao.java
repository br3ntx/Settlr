package com.settlr.dao.mysql;

import com.settlr.dao.UserDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.PayMethod;
import com.settlr.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** The users table in MySQL. */
public class MySqlUserDao extends AbstractDao<User> implements UserDao {

    private static final String COLUMNS =
            "user_id, username, display_name, password_hash, created_at, avatar_path, pay_method, pay_number, pay_name";

    /** The same columns with a table alias, e.g. "u.user_id, u.username, ..." for joins (MySqlMembershipDao). */
    static String columns(String alias) {
        return alias + "." + COLUMNS.replace(", ", ", " + alias + ".");
    }

    @Override
    public User findById(int id) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM users WHERE user_id = ?", "Could not load the user.", id);
    }

    @Override
    public User findByUsername(String username) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM users WHERE username = ?",
                "Could not look up that username.", username);
    }

    @Override
    public int insert(User user) throws DataAccessException {
        int id = insert("INSERT INTO users (username, display_name, password_hash, created_at) VALUES (?, ?, ?, ?)",
                "Could not create the account.",
                user.getUsername(), user.getDisplayName(), user.getPasswordHash(), user.getCreatedAt());
        user.setId(id);
        return id;
    }

    @Override
    public void update(User user) throws DataAccessException {
        update("UPDATE users SET username = ?, display_name = ?, password_hash = ?, avatar_path = ?, "
                        + "pay_method = ?, pay_number = ?, pay_name = ? WHERE user_id = ?",
                "Could not save the account.",
                user.getUsername(), user.getDisplayName(), user.getPasswordHash(), user.getAvatarPath(),
                user.getPayMethod(), user.getPayNumber(), user.getPayName(), user.getId());
    }

    /** Only works for users with no tasks, payments or memberships (the foreign keys block the rest). */
    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM users WHERE user_id = ?", "Could not delete the account.", id);
    }

    @Override
    protected User mapRow(ResultSet rs) throws SQLException {
        return read(rs);
    }

    /** One users row (selected with COLUMNS or columns(alias)) as a User. Shared with MySqlMembershipDao. */
    static User read(ResultSet rs) throws SQLException {
        User u = new User(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getObject("created_at", LocalDateTime.class));
        u.setAvatarPath(rs.getString("avatar_path"));
        String method = rs.getString("pay_method");
        try {
            u.setPaymentInfo(method == null ? null : PayMethod.valueOf(method),
                    rs.getString("pay_number"), rs.getString("pay_name"));
        } catch (IllegalArgumentException e) {
            u.setPaymentInfo(null, null, null); // bad data typed into the database by hand: treat as not set
        }
        return u;
    }
}
