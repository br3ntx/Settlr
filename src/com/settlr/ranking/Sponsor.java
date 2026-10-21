package com.settlr.ranking;

/**
 * Sponsor: total pesos of the expenses a member paid for.
 *
 * <p>Why the household check is inside the join on expenses: it keeps the totals of other
 * households out while still letting members with no expenses show up with 0.
 */
public class Sponsor extends SqlRankingCategory {

    public Sponsor() {
        super("Sponsor", "Paid for the most expenses. Libre mo naman!", false);
    }

    @Override
    protected String getSql() {
        return """
                SELECT u.user_id, u.display_name,
                       COALESCE(SUM(e.amount), 0) AS value,
                       RANK() OVER (ORDER BY COALESCE(SUM(e.amount), 0) DESC) AS rnk
                FROM household_members hm
                JOIN users u ON u.user_id = hm.user_id
                LEFT JOIN expenses e
                       ON e.paid_by = u.user_id
                      AND e.household_id = hm.household_id
                      AND e.created_at >= ?
                WHERE hm.household_id = ? AND hm.is_active = TRUE
                GROUP BY u.user_id, u.display_name
                ORDER BY rnk, u.display_name
                """;
    }

    @Override
    protected String formatValue(double v) {
        return formatPeso(v);
    }
}
