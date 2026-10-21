package com.settlr.ranking;

import com.settlr.exception.DataAccessException;
import com.settlr.model.LeaderboardEntry;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settlr Score: the overall ranking that mixes chores, payments and swaps into one number.
 *
 * <p>Why this extends RankingCategory directly and not SqlRankingCategory: the score is a
 * weighted sum, and the weights must live ONLY here (master doc section 4) so the group can
 * tune them in one place. So SQL just counts the raw facts (three small queries) and Java
 * applies the weights. MostImproved reuses scoresBetween() to score two different windows.
 * The score can be negative.
 */
public class SettlrScore extends RankingCategory {

    // ---- THE SCORING WEIGHTS: the only place in the whole project they are written ----
    /** On time task: + (task points x this). */
    private static final int ON_TIME_TASK_PER_POINT = 1;
    /** Late task: flat bonus, whatever its points. */
    private static final int LATE_TASK_BONUS = 1;
    /** Missed task: - (task points x this). */
    private static final int MISSED_TASK_PER_POINT = 1;
    /** Each confirmed on-time payment. */
    private static final int ON_TIME_PAYMENT_BONUS = 2;
    /** Each swap accepted (taking someone else's task). */
    private static final int SWAP_ACCEPTED_BONUS = 1;

    private static final String MEMBERS_SQL = """
            SELECT u.user_id, u.display_name
            FROM household_members hm
            JOIN users u ON u.user_id = hm.user_id
            WHERE hm.household_id = ? AND hm.is_active = TRUE
            ORDER BY u.display_name
            """;

    // Done tasks are dated by completed_at, missed ones by due_at (master doc section 4).
    private static final String TASKS_SQL = """
            SELECT ta.assignee_id AS user_id,
                   SUM(CASE WHEN ta.status = 'DONE'      THEN t.points ELSE 0 END) AS on_time_points,
                   SUM(CASE WHEN ta.status = 'DONE_LATE' THEN 1        ELSE 0 END) AS late_tasks,
                   SUM(CASE WHEN ta.status = 'MISSED'    THEN t.points ELSE 0 END) AS missed_points
            FROM task_assignments ta
            JOIN tasks t ON t.task_id = ta.task_id
            WHERE t.household_id = ?
              AND ( (ta.status IN ('DONE','DONE_LATE') AND ta.completed_at >= ? AND ta.completed_at < ?)
                 OR (ta.status = 'MISSED'              AND ta.due_at      >= ? AND ta.due_at      < ?) )
            GROUP BY ta.assignee_id
            """;

    // On time = paid_at on or before the END of the expense's due date (rule 3.6), so < due_date + 1 day.
    private static final String PAYMENTS_SQL = """
            SELECT p.from_user AS user_id, COUNT(*) AS on_time_payments
            FROM payments p
            JOIN expense_shares es ON es.share_id = p.share_id
            JOIN expenses e ON e.expense_id = es.expense_id
            WHERE e.household_id = ?
              AND p.status = 'CONFIRMED'
              AND p.paid_at >= ? AND p.paid_at < ?
              AND p.paid_at < DATE_ADD(e.due_date, INTERVAL 1 DAY)
            GROUP BY p.from_user
            """;

    // swap_requests has no household column, so the household is checked through the task.
    private static final String SWAPS_SQL = """
            SELECT sr.to_user AS user_id, COUNT(*) AS swaps_accepted
            FROM swap_requests sr
            JOIN task_assignments ta ON ta.assignment_id = sr.assignment_id
            JOIN tasks t ON t.task_id = ta.task_id
            WHERE t.household_id = ?
              AND sr.status = 'ACCEPTED'
              AND sr.responded_at >= ? AND sr.responded_at < ?
            GROUP BY sr.to_user
            """;

    /** A member's score for one window. A record is a small read-only data holder. */
    public record MemberScore(int userId, String displayName, int score) {
    }

    /** The raw counts for one member before the weights are applied. */
    private static final class Tally {
        private final String displayName;
        private int onTimePoints;
        private int lateTasks;
        private int missedPoints;
        private int onTimePayments;
        private int swapsAccepted;

        private Tally(String displayName) {
            this.displayName = displayName;
        }

        private int score() {
            return onTimePoints * ON_TIME_TASK_PER_POINT
                    + lateTasks * LATE_TASK_BONUS
                    - missedPoints * MISSED_TASK_PER_POINT
                    + onTimePayments * ON_TIME_PAYMENT_BONUS
                    + swapsAccepted * SWAP_ACCEPTED_BONUS;
        }
    }

    public SettlrScore() {
        super("Settlr Score", "Overall score from chores, payments and swaps. Ikaw ba ang pinaka-solid?", false);
    }

    @Override
    public List<LeaderboardEntry> compute(int householdId, RankingPeriod period) throws DataAccessException {
        LocalDate today = LocalDate.now();
        List<MemberScore> scores = scoresBetween(householdId, period.getStart(today), period.getEnd(today));

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (MemberScore m : scores) {
            entries.add(new LeaderboardEntry(m.userId(), m.displayName(), m.score(), 1, formatValue(m.score())));
        }
        assignRanks(entries); // the "1" above is a placeholder; real ranks are set here
        return entries;
    }

    /**
     * Score of every ACTIVE member for the window [from, to). Public so MostImproved can call it
     * for this week and for last week. Members with nothing in the window score 0.
     */
    public List<MemberScore> scoresBetween(int householdId, LocalDateTime from, LocalDateTime to)
            throws DataAccessException {
        Map<Integer, Tally> tallies = new LinkedHashMap<>();
        try (Connection con = DBConnection.getInstance().getConnection()) {
            loadMembers(con, householdId, tallies);
            loadTaskCounts(con, householdId, from, to, tallies);
            loadPaymentCounts(con, householdId, from, to, tallies);
            loadSwapCounts(con, householdId, from, to, tallies);
        } catch (SQLException e) {
            throw new DataAccessException("Couldn't load the Settlr Score. Please try again.", e);
        }

        List<MemberScore> result = new ArrayList<>();
        for (Map.Entry<Integer, Tally> e : tallies.entrySet()) {
            result.add(new MemberScore(e.getKey(), e.getValue().displayName, e.getValue().score()));
        }
        return result;
    }

    private void loadMembers(Connection con, int householdId, Map<Integer, Tally> tallies) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(MEMBERS_SQL)) {
            ps.setInt(1, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tallies.put(rs.getInt("user_id"), new Tally(rs.getString("display_name")));
                }
            }
        }
    }

    // In the three loaders below, "if (t != null)" skips people who are not active members any more.

    private void loadTaskCounts(Connection con, int householdId, LocalDateTime from, LocalDateTime to,
                                Map<Integer, Tally> tallies) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(TASKS_SQL)) {
            ps.setInt(1, householdId);
            ps.setObject(2, from);
            ps.setObject(3, to);
            ps.setObject(4, from);
            ps.setObject(5, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Tally t = tallies.get(rs.getInt("user_id"));
                    if (t != null) {
                        t.onTimePoints = rs.getInt("on_time_points");
                        t.lateTasks = rs.getInt("late_tasks");
                        t.missedPoints = rs.getInt("missed_points");
                    }
                }
            }
        }
    }

    private void loadPaymentCounts(Connection con, int householdId, LocalDateTime from, LocalDateTime to,
                                   Map<Integer, Tally> tallies) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(PAYMENTS_SQL)) {
            ps.setInt(1, householdId);
            ps.setObject(2, from);
            ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Tally t = tallies.get(rs.getInt("user_id"));
                    if (t != null) {
                        t.onTimePayments = rs.getInt("on_time_payments");
                    }
                }
            }
        }
    }

    private void loadSwapCounts(Connection con, int householdId, LocalDateTime from, LocalDateTime to,
                                Map<Integer, Tally> tallies) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SWAPS_SQL)) {
            ps.setInt(1, householdId);
            ps.setObject(2, from);
            ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Tally t = tallies.get(rs.getInt("user_id"));
                    if (t != null) {
                        t.swapsAccepted = rs.getInt("swaps_accepted");
                    }
                }
            }
        }
    }

    private String formatValue(double v) {
        long n = Math.round(v);
        return n + (Math.abs(n) == 1 ? " pt" : " pts");
    }
}
