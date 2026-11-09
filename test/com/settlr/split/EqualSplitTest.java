package com.settlr.split;

import com.settlr.model.ExpenseShare;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

/**
 * Checks that EqualSplit divides money to the centavo and never loses or adds a centavo.
 * Run it from VS Code's Testing tab (the flask icon), or right-click the file and pick Run Tests.
 */
public class EqualSplitTest {

    private final SplitStrategy split = new EqualSplit();

    @Test
    public void hundredPesosThreeWaysAddsBackToHundred() {
        List<ExpenseShare> shares = split.split(money("100.00"), 1, Arrays.asList(1, 2, 3));

        // the payer (user 1) takes the leftover centavo
        assertEquals(money("33.34"), shares.get(0).getAmountOwed());
        assertEquals(money("33.33"), shares.get(1).getAmountOwed());
        assertEquals(money("33.33"), shares.get(2).getAmountOwed());
        assertEquals(money("100.00"), total(shares));
    }

    @Test
    public void leftoverGoesToFirstPersonWhenPayerIsNotInTheSplit() {
        List<ExpenseShare> shares = split.split(money("10.00"), 9, Arrays.asList(4, 5, 6));

        assertEquals(money("3.34"), shares.get(0).getAmountOwed());
        assertEquals(money("3.33"), shares.get(1).getAmountOwed());
        assertEquals(money("10.00"), total(shares));
    }

    @Test
    public void oneCentavoBetweenTwoPeople() {
        List<ExpenseShare> shares = split.split(money("0.01"), 2, Arrays.asList(1, 2));

        assertEquals(money("0.00"), shares.get(0).getAmountOwed());
        assertEquals(money("0.01"), shares.get(1).getAmountOwed()); // user 2 paid
        assertEquals(money("0.01"), total(shares));
    }

    @Test
    public void onePersonOwesEverything() {
        List<ExpenseShare> shares = split.split(money("250.50"), 1, Arrays.asList(1));

        assertEquals(1, shares.size());
        assertEquals(money("250.50"), shares.get(0).getAmountOwed());
    }

    @Test
    public void payersOwnShareIsSettledRightAway() {
        List<ExpenseShare> shares = split.split(money("90.00"), 2, Arrays.asList(1, 2, 3));

        assertNull(shares.get(0).getSettledAt());
        assertNotNull(shares.get(1).getSettledAt()); // user 2 paid the bill
        assertNull(shares.get(2).getSettledAt());
    }

    @Test
    public void sharesAlwaysAddUpForManyRandomAmounts() {
        Random random = new Random(42); // fixed seed: the same "random" amounts every run
        for (int run = 0; run < 2000; run++) {
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(10_000_000), 2); // 0.01 to 100,000.00
            int people = 1 + random.nextInt(8);
            Integer[] ids = new Integer[people];
            for (int i = 0; i < people; i++) {
                ids[i] = i + 1;
            }
            List<ExpenseShare> shares = split.split(amount, 1, Arrays.asList(ids));
            assertEquals("Split of " + amount + " among " + people, amount, total(shares));
        }
    }

    @Test
    public void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class,
                () -> split.split(money("0.00"), 1, Arrays.asList(1, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> split.split(new BigDecimal("10.005"), 1, Arrays.asList(1, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> split.split(money("10.00"), 1, Arrays.asList(1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> split.split(money("10.00"), 1, Arrays.asList()));
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static BigDecimal total(List<ExpenseShare> shares) {
        BigDecimal sum = BigDecimal.ZERO.setScale(2);
        for (ExpenseShare s : shares) {
            sum = sum.add(s.getAmountOwed());
        }
        return sum;
    }
}
