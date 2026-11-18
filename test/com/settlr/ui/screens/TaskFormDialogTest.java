package com.settlr.ui.screens;

import com.settlr.model.OneTimeTask;
import com.settlr.model.Recurrence;
import com.settlr.model.RecurringTask;
import com.settlr.model.Task;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Checks that the New task form builds the right kind of task from its "Repeats" and "Turns" choices. */
public class TaskFormDialogTest {

    private static final LocalDateTime DUE = LocalDateTime.of(2026, 12, 5, 23, 59);

    @Test
    public void onceMakesAOneTimeTask() {
        Task task = TaskFormDialog.buildTask("Buy soap", DUE, 2, null, false);

        assertTrue(task instanceof OneTimeTask);
        assertFalse(task.repeats());
        assertNull(task.getRecurrence());
        assertEquals(DUE, task.getFirstDueAt());
        assertEquals(2, task.getDefaultAssigneeId());
    }

    @Test
    public void weeklyWithTakeTurnsMakesARotatingRecurringTask() {
        Task task = TaskFormDialog.buildTask("Mop floors", DUE, 3, Recurrence.WEEKLY, true);

        assertTrue(task instanceof RecurringTask);
        assertEquals(Recurrence.WEEKLY, task.getRecurrence());
        assertTrue(task.isRotates());
        assertEquals(DUE.plusWeeks(1), task.getNextDueDate(DUE));
    }

    @Test
    public void dailyWithSamePersonStaysWithTheAssignee() {
        Task task = TaskFormDialog.buildTask("Feed the cat", DUE, 4, Recurrence.DAILY, false);

        assertEquals(Recurrence.DAILY, task.getRecurrence());
        assertFalse(task.isRotates());
    }

    @Test
    public void theHintSaysHowOftenAndWhetherTurnsPass() {
        assertEquals("Done once, by the deadline.", TaskFormDialog.repeatHint(null, false));
        assertEquals("Every day, same person each time.", TaskFormDialog.repeatHint(Recurrence.DAILY, false));
        assertEquals("Every week on the same day, taking turns.", TaskFormDialog.repeatHint(Recurrence.WEEKLY, true));
    }
}
