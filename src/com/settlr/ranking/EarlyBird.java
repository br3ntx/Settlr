package com.settlr.ranking;

/**
 * Early Bird: how many tasks a member finished at least 24 hours before they were due.
 *
 * <p>Why COUNT(t.task_id) and not COUNT(ta.assignment_id): t is the table filtered by
 * household, so only tasks of this household are counted.
 */
public class EarlyBird extends SqlRankingCategory {

    public EarlyBird() {
        super("Early Bird", "Finished chores at least 24 hours early. Maaga pa, tapos na!", false);
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
                      AND ta.status IN ('DONE','DONE_LATE')
                      AND ta.completed_at >= ?
                      AND ta.completed_at <= DATE_SUB(ta.due_at, INTERVAL 24 HOUR)
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
        return countLabel(v, "task", "tasks");
    }
}
