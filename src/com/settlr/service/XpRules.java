package com.settlr.service;

import com.settlr.model.XpActivity;

/**
 * How much XP things are worth. The one place the XP rules live, so the DAO only counts
 * rows and the rules can be tested without a database (XpAndShopTest).
 */
public final class XpRules {

    /** XP for each task point done on time. */
    public static final int XP_PER_POINT = 10;
    /** XP for each task point done late: half. */
    public static final int XP_PER_POINT_LATE = 5;
    /** XP for each payment made on time. */
    public static final int XP_PER_PAYMENT = 20;

    private XpRules() { }

    /** XP earned from the given activity. */
    public static int earned(XpActivity activity) {
        if (activity == null) {
            return 0;
        }
        return activity.getOnTimePoints() * XP_PER_POINT
                + activity.getLatePoints() * XP_PER_POINT_LATE
                + activity.getOnTimePayments() * XP_PER_PAYMENT;
    }

    /** XP you can spend: what you earned, plus what you got back, minus what you spent. */
    public static int balance(XpActivity activity, int spentAndRefunded) {
        return earned(activity) + spentAndRefunded;
    }
}
