package com.settlr.util;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

/** The week runs Monday to Sunday everywhere in the app. */
public class DateTimeUtilTest {

    @Test
    public void weekStartsOnMonday() {
        LocalDateTime monday = LocalDateTime.of(2026, 11, 9, 0, 0);
        assertEquals(monday, DateTimeUtil.weekStart(LocalDate.of(2026, 11, 9)));  // Monday itself
        assertEquals(monday, DateTimeUtil.weekStart(LocalDate.of(2026, 11, 12))); // Thursday
        assertEquals(monday, DateTimeUtil.weekStart(LocalDate.of(2026, 11, 15))); // Sunday
    }

    @Test
    public void weekCanStartInTheMonthBefore() {
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), DateTimeUtil.weekStart(LocalDate.of(2026, 11, 1)));
    }
}
