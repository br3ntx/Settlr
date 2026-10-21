package com.settlr.ranking;

/**
 * "Mamaya Na" Award (shame ranking): how many tasks a member finished late.
 *
 * <p>Tie-break from the master doc: when two members have the same count, the one who was
 * late by more hours on average ranks higher. That average is computed in the inner query and
 * used as the second sort key of RANK(), so tied members are separated by RANK() itself, not
 * by a second pass in Java. The AVG ignores rows from other households (t.task_id IS NOT NULL).
 */
public class MamayaNa extends SqlRankingCategory {

    public MamayaNa() {
        super("\"Mamaya Na\" Award", "Most late chores. Mamaya na raw... pero late na!", true);
    }

    @Override
    protected String getSql() {
        return """
                SELECT r.user_id, r.display_name, r.value,
                       RANK() OVER (ORDER BY r.value DESC, r.avg_late DESC) AS rnk
                FROM (
                    SELECT u.user_id, u.display_name,
                           COUNT(t.task_id) AS value,
                           COALESCE(AVG(CASE WHEN t.task_id IS NOT NULL
                                             THEN TIMESTAMPDIFF(MINUTE, ta.due_at, ta.completed_at) END) / 60, 0) AS avg_late
                    FROM household_members hm
                    JOIN users u ON u.user_id = hm.user_id
                    LEFT JOIN task_assignments ta
                           ON ta.assignee_id = u.user_id
                          AND ta.status = 'DONE_LATE'
                          AND ta.completed_at >= ?
                    LEFT JOIN tasks t
                           ON t.task_id = ta.task_id
                          AND t.household_id = hm.household_id
                    WHERE hm.household_id = ? AND hm.is_active = TRUE
                    GROUP BY u.user_id, u.display_name
                ) r
                ORDER BY rnk, r.display_name
                """;
    }

    @Override
    protected String formatValue(double v) {
        return countLabel(v, "late task", "late tasks");
    }
}
