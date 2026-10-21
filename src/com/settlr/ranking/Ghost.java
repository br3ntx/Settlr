package com.settlr.ranking;

/**
 * Ghost (shame ranking): how many tasks a member missed.
 *
 * <p>A missed task has no completed_at, so the master doc places it in a period by its due_at.
 */
public class Ghost extends SqlRankingCategory {

    public Ghost() {
        super("Ghost", "Most missed chores. Nasaan ka na?", true);
    }

    @Override
    protected String getSql() {
        return """
                SELECT u.user_id, u.display_name,
                       COUNT(t.task_id) AS value,
                       RANK() OVER (ORDER BY COUNT(t.task_id) DESC) AS rnk
                FROM household_members hm
                JOIN users u ON u.user_id = hm.user_id
                LEFT JOIN task_assignments ta
                       ON ta.assignee_id = u.user_id
                      AND ta.status = 'MISSED'
                      AND ta.due_at >= ?
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
        return countLabel(v, "missed task", "missed tasks");
    }
}
