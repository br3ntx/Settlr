package com.settlr.model;

/**
 * What kind of expense it is. Names match the SQL ENUM values exactly, so
 * ExpenseCategory.valueOf(rs.getString("category")) works.
 */
public enum ExpenseCategory {
    RENT,
    ELECTRICITY,
    WATER,
    WIFI,
    GROCERIES,
    OTHER
}