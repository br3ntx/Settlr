package com.settlr.ranking;

/**
 * Chore Champion: total points of the tasks a member finished (on time or late).
 *
 * <p>The query is the master doc's example. Why t.points is summed from the tasks table
 * and the household check sits on that join: an assignment from another household then
 * finds no matching task and adds nothing.
 */
public class ChoreChampion extends SqlRankingCategory {

    public ChoreChampion() {
        super("Chore Champion", "Most chore points finished. Sipag at tiyaga!", false);
    }

    @Override
    protected String getSql() {
        return """
                SELECT u.user_id, u.display_name,
                       COALESCE(SUM(t.points), 0) AS value,
                       RANK() OVER (ORDER BY COALESCE(SUM(t.points), 0) DESC) AS rnk
                FROM household_members hm
                JOIN users u ON u.user_id = hm.user_id
                LEFT JOIN task_assignments ta
                       ON ta.assignee_id = u.user_id
                      AND ta.status IN ('DONE','DONE_LATE')
                      AND ta.completed_at >= ?
                LEFT JOIN tasks t
                       ON t.task_id = ta.task_id
                      AND t.household_id = hm.household_id
                WHERE hm.household_id = ? AND hm.is_active = TRUE
                GROUP BY u.user_id, u.display_name
                ORDER BY rnk, u.display_name
                """;
    }

    @Override
    protected String formatValue(double v) {
        return countLabel(v, "pt", "pts");
    }
}
