package com.settlr.model;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Checks that each kind of task answers for itself (polymorphism): its next due date,
 * who gets the next turn, and whether its schedule is allowed.
 */
public class TaskScheduleTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 2, 20, 0);
    private final List<User> members = Arrays.asList(user(1, "Ana"), user(2, "Ben"), user(3, "Carlo"));

    @Test
    public void aRotatingTaskGoesToTheNextMemberAndWrapsAround() {
        Task dishes = new RecurringTask("Dishes", null, 2, START, 1, Recurrence.DAILY, true);

        assertEquals(2, dishes.nextAssignee(1, members));
        assertEquals(3, dishes.nextAssignee(2, members));
        assertEquals(1, dishes.nextAssignee(3, members));
    }

    @Test
    public void aRotatingTaskStartsOverIfTheLastPersonLeft() {
        Task dishes = new RecurringTask("Dishes", null, 2, START, 1, Recurrence.DAILY, true);

        assertEquals(1, dishes.nextAssignee(99, members));
    }

    @Test
    public void aNonRotatingTaskStaysWithItsPerson() {
        Task bathroom = new RecurringTask("Bathroom", null, 3, START, 3, Recurrence.WEEKLY, false);

        assertEquals(3, bathroom.nextAssignee(3, members));
        assertEquals(1, bathroom.nextAssignee(3, members.subList(0, 2))); // Carlo left: first member
    }

    @Test
    public void aOneTimeTaskHasNoSecondDueDate() {
        Task soap = new OneTimeTask("Buy soap", null, 1, START, 2);

        assertFalse(soap.repeats());
        assertNull(soap.getRecurrence());
        assertEquals(START, soap.getNextDueDate(START.minusDays(1)));
        assertNull(soap.getNextDueDate(START));
    }

    @Test
    public void aRepeatingTaskKeepsItsTimeOfDay() {
        Task weekly = new RecurringTask("Trash", null, 1, START, 1, Recurrence.WEEKLY, true);

        assertTrue(weekly.repeats());
        assertEquals(START.plusWeeks(2), weekly.getNextDueDate(START.plusWeeks(1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aMonthlyTaskCantStartAfterThe28th() {
        new RecurringTask("Rent", null, 1, START.withDayOfMonth(29), 1, Recurrence.MONTHLY, false).checkSchedule();
    }

    @Test
    public void aWeeklyTaskCanStartOnThe31st() {
        new RecurringTask("Trash", null, 1, LocalDateTime.of(2026, 12, 31, 9, 0), 1, Recurrence.WEEKLY, false)
                .checkSchedule(); // no exception
    }

    /** A member for the rotation tests. */
    private static User user(int id, String name) {
        return new User(id, name.toLowerCase(), name, "p1$AAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                START.minusDays(30));
    }
}
