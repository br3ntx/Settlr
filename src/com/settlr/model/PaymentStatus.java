package com.settlr.model;

/**
 * Where a payment is in its life: waiting for the receiver, accepted, or refused.
 * Names match the SQL ENUM values exactly, so
 * PaymentStatus.valueOf(rs.getString("status")) works.
 */
public enum PaymentStatus {
    PENDING, CONFIRMED, REJECTED
}