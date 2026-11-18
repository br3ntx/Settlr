package com.settlr.ui.screens;

import com.settlr.service.PendingPayment;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;

import java.awt.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// DESIGN PATTERN: Adapter (Client) - one confirm popup handles both kinds of payment, through PendingPayment.
/**
 * The "Did Dani pay you?" popup for a PendingPayment: who sent it, what they say they sent,
 * and a breakdown of the expenses it covers (shares of yours that cancel out show as minus),
 * with the total at the bottom. Used by Confirm on Expenses and on Home.
 *
 *     ConfirmPaymentDialog.open(this, payment, null, () -> payment.confirm());
 */
final class ConfirmPaymentDialog {

    /** More rows than this and the rest are added up into one "and N more" row. */
    private static final int MAX_ROWS = 6;
    private static final int MAX_LABEL = 30;

    private ConfirmPaymentDialog() { }

    /**
     * @param extra     a sentence added after the explanation, or null
     * @param onConfirm runs after "Yes, I got it" (the popup is already closed)
     */
    static void open(Component over, PendingPayment p, String extra, Runnable onConfirm) {
        String line = p.getConfirmExplanation() + (extra == null ? "" : " " + extra);
        ConfirmDialog dialog = new ConfirmDialog(p.getConfirmTitle(), line).avatar(p.getFromName());
        List<PendingPayment.Line> lines = p.getBreakdown();
        if (!lines.isEmpty()) {
            dialog.table(rows(lines), p.getFromName() + " paid you", MoneyUtil.format(p.getAmount()), Theme.MINT);
        }
        dialog.buttons("Not yet", "Yes, I got it", false, onConfirm).showOver(over);
    }

    private static String[][] rows(List<PendingPayment.Line> lines) {
        List<String[]> rows = new ArrayList<>();
        int shown = lines.size() <= MAX_ROWS ? lines.size() : MAX_ROWS - 1;
        for (int i = 0; i < shown; i++) {
            rows.add(new String[] {shorten(lines.get(i).getLabel()), money(lines.get(i).getAmount())});
        }
        if (shown < lines.size()) {
            BigDecimal rest = BigDecimal.ZERO;
            for (int i = shown; i < lines.size(); i++) {
                rest = rest.add(lines.get(i).getAmount());
            }
            rows.add(new String[] {"and " + (lines.size() - shown) + " more", money(rest)});
        }
        return rows.toArray(new String[0][]);
    }

    /** "P2,400.00", or "\u2212P333.33" for a share that cancels out. */
    private static String money(BigDecimal amount) {
        return amount.signum() < 0 ? "\u2212" + MoneyUtil.format(amount.negate()) : MoneyUtil.format(amount);
    }

    private static String shorten(String label) {
        return label.length() <= MAX_LABEL ? label : label.substring(0, MAX_LABEL - 1).trim() + "\u2026";
    }
}
