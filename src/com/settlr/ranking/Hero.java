package com.settlr.ranking;

/**
 * Hero: how many swap requests a member accepted, meaning they took over someone's task.
 *
 * <p>swap_requests has no household column, so the household is checked through the
 * assignment's task. COUNT(t.task_id) then ignores swaps that belong to another household.
 */
public class Hero extends SqlRankingCategory {

    public Hero() {
        super("Hero", "Took over the most chores for roommates. Bayani ng bahay!", false);
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
                       ON sr.to_user = u.user_id
                      AND sr.status = 'ACCEPTED'
                      AND sr.responded_at >= ?
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
        return countLabel(v, "swap", "swaps");
    }
}
