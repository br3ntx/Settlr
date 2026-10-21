package com.settlr.ranking;

import com.settlr.exception.DataAccessException;
import com.settlr.model.LeaderboardEntry;
import com.settlr.util.DBConnection;
import com.settlr.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streak: how many full weeks (Monday to Sunday), counting back from LAST week, a member kept
 * a clean record: at least one task finished on time and none late or missed.
 *
 * <p>Why Java and not one SQL query: "count the unbroken run of weeks" needs a loop that stops
 * at the first bad week, which is natural in Java and awkward in SQL. SQL only hands back the
 * facts (which week each finished/missed task belongs to); Java walks the weeks.
 * The period is ignored, so the streak is the same on every tab (master doc section 4).
 *
 * <p>Weeks follow the section 4 date rule: done and late tasks sit in the week of completed_at,
 * missed tasks in the week of due_at. The current, unfinished week is never part of a streak.
 */
public class Streak extends RankingCategory {

    private static final int ON_TIME = 0;
    private static final int LATE = 1;
    private static final int MISSED = 2;

    private static final String MEMBERS_SQL = """
            SELECT u.user_id, u.display_name
            FROM household_members hm
            JOIN users u ON u.user_id = hm.user_id
            WHERE hm.household_id = ? AND hm.is_active = TRUE
            ORDER BY u.display_name
            """;

    // One row per finished or missed task, labelled with the Monday of the week it counts in.
    // WEEKDAY() is 0 for Monday, so date minus WEEKDAY days is that week's Monday.
    private static final String EVENTS_SQL = """
            SELECT ta.assignee_id AS user_id,
                   ta.status AS status,
                   DATE_SUB(DATE(CASE WHEN ta.status = 'MISSED' THEN ta.due_at ELSE ta.completed_at END),
                            INTERVAL WEEKDAY(CASE WHEN ta.status = 'MISSED' THEN ta.due_at ELSE ta.completed_at END) DAY)
                       AS week_start
            FROM task_assignments ta
            JOIN tasks t ON t.task_id = ta.task_id
            WHERE t.household_id = ?
              AND ta.status IN ('DONE','DONE_LATE','MISSED')
            """;

    public Streak() {
        super("Streak", "Weeks in a row with chores done and none late or missed. Tuloy-tuloy lang!", false);
    }

    @Override
    public List<LeaderboardEntry> compute(int householdId, RankingPeriod period) throws DataAccessException {
        LocalDate thisMonday = DateTimeUtil.weekStart(LocalDate.now()).toLocalDate();

        Map<Integer, String> members = new LinkedHashMap<>();
        // user id -> (Monday of the week -> {on time, late, missed} counts)
        Map<Integer, Map<LocalDate, int[]>> weeks = new HashMap<>();
        try (Connection con = DBConnection.getInstance().getConnection()) {
            loadMembers(con, householdId, members);
            loadWeeklyCounts(con, householdId, weeks);
        } catch (SQLException e) {
            throw new DataAccessException("Couldn't load the Streak ranking. Please try again.", e);
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, String> m : members.entrySet()) {
            int streak = countStreak(weeks.get(m.getKey()), thisMonday);
            entries.add(new LeaderboardEntry(m.getKey(), m.getValue(), streak, 1, formatValue(streak)));
        }
        assignRanks(entries);
        return entries;
    }

    /** Walks backwards from last week and stops at the first week that is not clean. */
    private int countStreak(Map<LocalDate, int[]> memberWeeks, LocalDate thisMonday) {
        int streak = 0;
        LocalDate week = thisMonday.minusWeeks(1);
        while (memberWeeks != null) {
            int[] counts = memberWeeks.get(week);
            boolean clean = counts != null
                    && counts[ON_TIME] >= 1 && counts[LATE] == 0 && counts[MISSED] == 0;
            if (!clean) {
                break;
            }
            streak++;
            week = week.minusWeeks(1);
        }
        return streak;
    }

    private void loadMembers(Connection con, int householdId, Map<Integer, String> members) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(MEMBERS_SQL)) {
            ps.setInt(1, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.put(rs.getInt("user_id"), rs.getString("display_name"));
                }
            }
        }
    }

    private void loadWeeklyCounts(Connection con, int householdId, Map<Integer, Map<LocalDate, int[]>> weeks)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(EVENTS_SQL)) {
            ps.setInt(1, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int[] counts = weeks
                            .computeIfAbsent(rs.getInt("user_id"), id -> new HashMap<>())
                            .computeIfAbsent(rs.getObject("week_start", LocalDate.class), monday -> new int[3]);
                    switch (rs.getString("status")) {
                        case "DONE" -> counts[ON_TIME]++;
                        case "DONE_LATE" -> counts[LATE]++;
                        case "MISSED" -> counts[MISSED]++;
                        default -> { /* the query only returns the three statuses above */ }
                    }
                }
            }
        }
    }

    private String formatValue(double v) {
        return countLabel(v, "week", "weeks");
    }
}
