package com.settlr.ranking;

/**
 * Slow Settler (shame ranking): average days between an expense being created and the
 * member's confirmed payment for it.
 *
 * <p>Only CONFIRMED payments count (a rejected or pending one has not settled anything). The
 * average is in days, rounded to 1 decimal in SQL so the shown value and the rank agree.
 * A member with no payment gets 0 and shows a dash, because "0.0 days" would look like a fast payer.
 */
public class SlowSettler extends SqlRankingCategory {

    public SlowSettler() {
        super("Slow Settler", "Takes the longest to pay back. Kupad ba?", true);
    }

    @Override
    protected String getSql() {
        return """
                SELECT r.user_id, r.display_name, r.value,
                       RANK() OVER (ORDER BY r.value DESC) AS rnk
                FROM (
                    SELECT u.user_id, u.display_name,
                           COALESCE(ROUND(AVG(CASE WHEN e.expense_id IS NOT NULL
                                                   THEN TIMESTAMPDIFF(SECOND, e.created_at, p.paid_at) END) / 86400, 1), 0) AS value
                    FROM household_members hm
                    JOIN users u ON u.user_id = hm.user_id
                    LEFT JOIN payments p
                           ON p.from_user = u.user_id
                          AND p.status = 'CONFIRMED'
                          AND p.paid_at >= ?
                    LEFT JOIN expense_shares es ON es.share_id = p.share_id
                    LEFT JOIN expenses e
                           ON e.expense_id = es.expense_id
                          AND e.household_id = hm.household_id
                    WHERE hm.household_id = ? AND hm.is_active = TRUE
                    GROUP BY u.user_id, u.display_name
                ) r
                ORDER BY rnk, r.display_name
                """;
    }

    @Override
    protected String formatValue(double v) {
        if (v <= 0) {
            return "\u2014"; // em dash: no confirmed payment in this period
        }
        return String.format(java.util.Locale.US, "%.1f days", v);
    }
}
