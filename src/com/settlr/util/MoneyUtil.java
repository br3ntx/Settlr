package com.settlr.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Money helpers, so every screen shows pesos the same way.
 * Money is always BigDecimal in Settlr, never double, because double can't
 * store values like 0.10 exactly and totals would drift by centavos.
 */
public final class MoneyUtil {

    /** The peso sign, written as a Unicode escape so it survives any file encoding. */
    public static final String PESO = "\u20B1";

    private MoneyUtil() { }

    /** 1250 -> "(peso)1,250.00", -50 -> "-(peso)50.00", null -> "(peso)0.00". */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        // A new DecimalFormat each call, because DecimalFormat isn't thread-safe.
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String digits = df.format(amount.abs().setScale(2, RoundingMode.HALF_UP));
        return (amount.signum() < 0 ? "-" + PESO : PESO) + digits;
    }

    /**
     * Splits an amount into equal parts, rounded down to the centavo.
     * Leftover centavos go to the first parts, one each, so the parts always
     * add up to exactly the original amount.
     * Example: splitEvenly(100.00, 3) -> [33.34, 33.33, 33.33]
     */
    public static List<BigDecimal> splitEvenly(BigDecimal amount, int parts) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must be zero or more.");
        }
        if (parts <= 0) {
            throw new IllegalArgumentException("Need at least one part to split into.");
        }
        BigDecimal base = amount.divide(BigDecimal.valueOf(parts), 2, RoundingMode.DOWN);
        BigDecimal used = base.multiply(BigDecimal.valueOf(parts));
        int leftoverCentavos = amount.subtract(used).movePointRight(2).intValueExact();

        List<BigDecimal> result = new ArrayList<>();
        for (int i = 0; i < parts; i++) {
            result.add(i < leftoverCentavos ? base.add(new BigDecimal("0.01")) : base);
        }
        return result;
    }
}
