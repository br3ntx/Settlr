package com.settlr.model;

/**
 * State of a swap request. Names match the SQL ENUM values exactly so
 * SwapStatus.valueOf(rs.getString("status")) works.
 */
public enum SwapStatus {
    PENDING, ACCEPTED, DECLINED
}
