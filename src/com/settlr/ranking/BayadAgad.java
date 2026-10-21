package com.settlr.ranking;

/**
 * Bayad Agad: the percentage of a member's confirmed payments that were on time.
 *
 * <p>On time means paid_at is on or before the END of the expense's due date (master doc 3.6),
 * so the query joins payments to expenses and compares with due_date + 1 day. Members with no
 * confirmed payment in the period are left out (HAVING), because "0%" would wrongly say they paid late.
 * The percentage is rounded in SQL so that two members who both show "67%" also share a rank.
 */
public class BayadAgad extends SqlRankingCategory {

    public BayadAgad() {
        super("Bayad Agad", "Pays what they owe on time. Bayad agad, walang utang!", false);
    }

    @Override
    protected String getSql() {
        return """
                SELECT r.user_id, r.display_name, r.value,
                       RANK() OVER (ORDER BY r.value DESC) AS rnk
                FROM (
                    SELECT u.user_id, u.display_name,
                           ROUND(100.0 * SUM(CASE WHEN p.paid_at < DATE_ADD(e.due_date, INTERVAL 1 DAY)
                                                  THEN 1 ELSE 0 END)
                                 / COUNT(e.expense_id), 0) AS value
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
                    HAVING COUNT(e.expense_id) > 0
                ) r
                ORDER BY rnk, r.display_name
                """;
    }

    @Override
    protected String formatValue(double v) {
        return String.format(java.util.Locale.US, "%.0f%%", v);
    }
}
