/** Saves a new payment on its own connection and returns its id. */
@Override
public int insert(Payment p) throws DataAccessException {
    try (Connection con = DBConnection.getInstance().getConnection()) {
        return insert(con, p);
    } catch (SQLException ex) {
        throw new DataAccessException("Could not save the payment.", ex);
    }
}

/** Saves a new payment using a connection the caller controls (for transactions). */
public int insert(Connection con, Payment p) throws DataAccessException {
    String sql = "INSERT INTO payments (share_id, from_user, to_user, amount, paid_at, "
            + "status, confirmed_at, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        ps.setInt(1, p.getShareId());
        ps.setInt(2, p.getFromUser());
        ps.setInt(3, p.getToUser());
        ps.setBigDecimal(4, p.getAmount());
        ps.setObject(5, p.getPaidAt());
        ps.setString(6, p.getStatus().name());
        if (p.getConfirmedAt() == null) {
            ps.setNull(7, Types.TIMESTAMP);
        } else {
            ps.setObject(7, p.getConfirmedAt());
        }
        ps.setString(8, p.getNote()); // setString accepts null for an empty note
        ps.executeUpdate();
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        throw new SQLException("No generated id was returned.");
    } catch (SQLException ex) {
        throw new DataAccessException("Could not save the payment.", ex);
    }
}

/** Saves the status, confirmed time and note on its own connection. */
@Override
public void update(Payment p) throws DataAccessException {
    try (Connection con = DBConnection.getInstance().getConnection()) {
        update(con, p);
    } catch (SQLException ex) {
        throw new DataAccessException("Could not update the payment.", ex);
    }
}

/** Same update, but inside the caller's transaction. */
public void update(Connection con, Payment p) throws DataAccessException {
    String sql = "UPDATE payments SET status = ?, confirmed_at = ?, note = ? WHERE payment_id = ?";
    try (PreparedStatement ps = con.prepareStatement(sql)) {
        ps.setString(1, p.getStatus().name());
        if (p.getConfirmedAt() == null) {
            ps.setNull(2, Types.TIMESTAMP);
        } else {
            ps.setObject(2, p.getConfirmedAt());
        }
        ps.setString(3, p.getNote());
        ps.setInt(4, p.getId());
        ps.executeUpdate();
    } catch (SQLException ex) {
        throw new DataAccessException("Could not update the payment.", ex);
    }
}