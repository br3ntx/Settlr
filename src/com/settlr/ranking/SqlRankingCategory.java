package com.settlr.ranking;

import com.settlr.exception.DataAccessException;
import com.settlr.model.LeaderboardEntry;
import com.settlr.util.DBConnection;
import com.settlr.util.MoneyUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A ranking whose numbers come from one SQL query.
 *
 * <p>This is the TEMPLATE METHOD pattern: compute() is written once, here, and never changes.
 * It runs the query, binds the parameters and turns rows into LeaderboardEntry objects.
 * Each of the 10 SQL rankings only supplies the two parts that differ: getSql() and
 * formatValue(). compute() is final so no subclass can break the shared steps.
 *
 * <p>Every query must return the columns user_id, display_name, value and rnk (the RANK()).
 */
public abstract class SqlRankingCategory extends RankingCategory {

    protected SqlRankingCategory(String title, String description, boolean shame) {
        super(title, description, shame);
    }

    /** The full SELECT. Its ? marks are bound by bindParameters(). */
    protected abstract String getSql();

    /** Turns the raw number into the text people read ("12 pts", "85%", "₱450.00"). */
    protected abstract String formatValue(double v);

    /**
     * Fills the ? marks. The default matches the master doc's example query: period start
     * first, household id second. A ranking whose query is shaped differently (UtangKing has
     * no period) overrides this one small method instead of rewriting compute().
     */
    protected void bindParameters(PreparedStatement ps, LocalDateTime periodStart, int householdId)
            throws SQLException {
        ps.setObject(1, periodStart);
        ps.setInt(2, householdId);
    }

    @Override
    public final List<LeaderboardEntry> compute(int householdId, RankingPeriod period)
            throws DataAccessException {
        LocalDateTime periodStart = period.getStart(LocalDate.now());
        List<LeaderboardEntry> entries = new ArrayList<>();

        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(getSql())) {
            bindParameters(ps, periodStart, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double value = rs.getDouble("value");
                    entries.add(new LeaderboardEntry(
                            rs.getInt("user_id"),
                            rs.getString("display_name"),
                            value,
                            rs.getInt("rnk"),
                            formatValue(value)));
                }
            }
        } catch (SQLException e) {
            // The user sees only this friendly text; the SQL error never reaches the screen.
            throw new DataAccessException("Couldn't load the " + getTitle() + " ranking. Please try again.", e);
        }
        return entries;
    }

    /** Pesos for money rankings. Goes through BigDecimal and MoneyUtil so every screen formats money the same. */
    protected static String formatPeso(double v) {
        return MoneyUtil.format(BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP));
    }
}
