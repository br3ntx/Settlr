package com.settlr.ranking;

/**
 * Dodger (shame ranking): how many swap requests a member SENT (asked someone else to take their task).
 *
 * <p>Every sent request counts, whatever its status, and is dated by created_at. As in Hero,
 * the household comes from the assignment's task because swap_requests has no household column.
 */
public class Dodger extends SqlRankingCategory {

    public Dodger() {
        super("Dodger", "Sent the most swap requests. Iwas-chore mode!", true);
    }

    @Override
    protected String getSql() {
        return """
                SELECT u.user_id, u.display_name,
                       COUNT(t.task_id) AS value,
                       RANK() OVER (ORDER BY COUNT(t.task_id) DESC) AS rnk
                FROM household_members hm
                JOIN users u ON u.user_id = hm.user_id
                LEFT JOIN swap_requests sr
                       ON sr.from_user = u.user_id
                      AND sr.created_at >= ?
                LEFT JOIN task_assignments ta ON ta.assignment_id = sr.assignment_id
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
        return countLabel(v, "request", "requests");
    }
}
