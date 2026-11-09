package com.settlr.model;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/** When a recurring bill is due next, and the expense it makes each month. */
public class RecurringBillTest {

    @Test
    public void dueLaterThisMonth() {
        assertEquals(LocalDate.of(2026, 11, 15), bill(15).nextDueDate(LocalDate.of(2026, 11, 9)));
    }

    @Test
    public void dueTodayCountsAsThisMonth() {
        assertEquals(LocalDate.of(2026, 11, 9), bill(9).nextDueDate(LocalDate.of(2026, 11, 9)));
    }

    @Test
    public void alreadyPassedMovesToNextMonth() {
        assertEquals(LocalDate.of(2026, 12, 1), bill(1).nextDueDate(LocalDate.of(2026, 11, 9)));
    }

    @Test
    public void passedAtTheEndOfTheYearMovesToJanuary() {
        assertEquals(LocalDate.of(2027, 1, 5), bill(5).nextDueDate(LocalDate.of(2026, 12, 20)));
    }

    @Test
    public void monthlyExpenseIsMadeFromTheBill() {
        Expense e = bill(28).createExpenseFor(YearMonth.of(2027, 2));

        assertEquals("WiFi", e.getTitle());
        assertEquals(Integer.valueOf(7), e.getBillId());
        assertEquals(LocalDate.of(2027, 2, 28), e.getDueDate());
        assertEquals(new BigDecimal("1500.00"), e.getAmount());
    }

    @Test
    public void shareEachIsRoundedToTheCentavo() {
        assertEquals(new BigDecimal("500.00"), bill(5).shareEach());
    }

    private static RecurringBill bill(int dueDay) {
        return new RecurringBill(7, 1, "WiFi", ExpenseCategory.WIFI, new BigDecimal("1500.00"), 2, dueDay,
                true, null, 2, LocalDateTime.now(), Arrays.asList(1, 2, 3));
    }
}
