package com.settlr.model;

/**
 * How someone wants to be paid ("How to pay me" on the Profile page).
 * Names match the SQL ENUM in users.pay_method.
 */
public enum PayMethod {
    GCASH("GCash", "Number"),
    MAYA("Maya", "Number"),
    BANK("Bank transfer", "Bank and account number"),
    CASH("Cash only", null);

    private final String label;
    private final String numberLabel;

    PayMethod(String label, String numberLabel) {
        this.label = label;
        this.numberLabel = numberLabel;
    }

    /** "GCash", "Bank transfer", ... */
    public String getLabel() { return label; }

    /** What the number field is called for this method, or null when there's no number (cash). */
    public String getNumberLabel() { return numberLabel; }

    /** True when people need a number and account name to send money this way. */
    public boolean needsAccount() { return numberLabel != null; }
}
