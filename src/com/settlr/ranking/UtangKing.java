package com.settlr.ranking;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Utang King/Queen (shame ranking): how much money a member still owes right now.
 *
 * <p>Debt is a current balance, not something that happened "this week", so the period is
 * ignored and the tab makes no difference. That also means the query has only ONE ? (the
 * household), so this class overrides bindParameters() instead of using the default.
 * Shares with a payment still waiting for confirmation or rejected stay unsettled, so they count.
 */
public class UtangKing extends SqlRankingCategory {

    public UtangKing() {
        super("Utang King/Queen", "Owes the most right now. Magbayad na, ha?", true);
    }

    @Override
    protected String getSql() {
        return """
                SELECT r.user_id, r.display_name, r.value,
                       RANK() OVER (ORDER BY r.value DESC) AS rnk
                FROM (
                    SELECT u.user_id, u.display_name,
                           COALESCE(SUM(CASE WHEN e.expense_id IS NOT NULL
                                             THEN es.amount_owed ELSE 0 END), 0) AS value
                    FROM household_members hm
                    JOIN users u ON u.user_id = hm.user_id
                    LEFT JOIN expense_shares es
                           ON es.user_id = u.user_id
                          AND es.settled_at IS NULL
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
    protected void bindParameters(PreparedStatement ps, LocalDateTime periodStart, int householdId)
            throws SQLException {
        ps.setInt(1, householdId); // the only ?, and the period is deliberately unused
    }

    @Override
    protected String formatValue(double v) {
        return formatPeso(v);
    }
}
