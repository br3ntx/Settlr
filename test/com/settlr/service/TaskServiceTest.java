package com.settlr.service;

import com.settlr.exception.PermissionDeniedException;
import com.settlr.exception.ValidationException;
import com.settlr.model.OneTimeTask;
import com.settlr.model.Recurrence;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Checks the task rules: first turns, on time vs late, who may tick a task off, and rotation. */
public class TaskServiceTest {

    private TestHousehold h;

    /** Before each test: a fresh test household in memory, with Ana (the Admin) logged in. */
    @Before
    public void setUp() throws Exception {
        h = new TestHousehold();
        h.logIn(h.ana);
    }

    /** After each test: logs out. */
    @After
    public void tearDown() {
        h.logOut();
    }

    @Test
    public void creatingATaskAlsoCreatesItsFirstTurn() throws Exception {
        Task task = h.tasks().createTask(new OneTimeTask("Buy soap", null, 1, h.now.plusDays(1), h.ben.getId()));

        TaskAssignment turn = h.onlyTurnOf(task);
        assertEquals(h.ben.getId(), turn.getAssigneeId());
        assertEquals(h.now.plusDays(1), turn.getDueAt());
        assertEquals(TaskStatus.PENDING, turn.getStatus());
    }

    @Test
    public void tickingOffBeforeTheDeadlineIsDoneAndAfterItIsDoneLate() throws Exception {
        Task early = h.oneTimeTask("Mop", 2, h.ana, h.now.plusHours(3));
        Task late = h.oneTimeTask("Dishes", 2, h.ana, h.now.minusHours(3));

        h.tasks().completeAssignment(h.onlyTurnOf(early).getId());
        h.tasks().completeAssignment(h.onlyTurnOf(late).getId());

        assertEquals(TaskStatus.DONE, h.onlyTurnOf(early).getStatus());
        assertEquals(TaskStatus.DONE_LATE, h.onlyTurnOf(late).getStatus());
    }

    @Test(expected = PermissionDeniedException.class)
    public void aMemberCantTickOffSomeoneElsesTask() throws Exception {
        Task task = h.oneTimeTask("Trash", 1, h.ben, h.now.plusHours(3));
        h.logIn(h.carlo); // not Ben, not an Admin

        h.tasks().completeAssignment(h.onlyTurnOf(task).getId());
    }

    @Test
    public void anAdminCanTickOffSomeoneElsesTask() throws Exception {
        Task task = h.oneTimeTask("Trash", 1, h.ben, h.now.plusHours(3)); // Ana is the Admin

        h.tasks().completeAssignment(h.onlyTurnOf(task).getId());

        assertEquals(TaskStatus.DONE, h.onlyTurnOf(task).getStatus());
    }

    @Test(expected = ValidationException.class)
    public void aMissedTaskCantBeTickedOff() throws Exception {
        Task task = h.oneTimeTask("Trash", 1, h.ana, h.now.minusDays(3));
        TaskAssignment turn = h.onlyTurnOf(task);
        turn.setStatus(TaskStatus.MISSED);
        h.db.assignments().update(turn);

        h.tasks().completeAssignment(turn.getId());
    }

    @Test
    public void upcomingTurnsOfARotatingTaskGoThroughEveryoneInJoinOrder() throws Exception {
        Task dishes = h.repeatingTask("Dishes", Recurrence.DAILY, true, h.ana, h.now.plusHours(2));

        List<TaskService.Turn> turns = h.tasks().upcomingTurns(h.onlyTurnOf(dishes), 4);

        assertEquals("Ana", turns.get(0).getUser().getDisplayName());
        assertEquals("Ben", turns.get(1).getUser().getDisplayName());
        assertEquals("Carlo", turns.get(2).getUser().getDisplayName());
        assertEquals("Ana", turns.get(3).getUser().getDisplayName()); // and around again
        assertEquals(h.now.plusHours(2).plusDays(3), turns.get(3).getDueAt());
    }

    @Test
    public void aOneTimeTaskHasJustOneTurn() throws Exception {
        Task task = h.oneTimeTask("Fix wifi", 3, h.carlo, h.now.plusDays(2));

        assertEquals(1, h.tasks().upcomingTurns(h.onlyTurnOf(task), 4).size());
    }

    @Test(expected = ValidationException.class)
    public void aTaskCantChangeFromRepeatingToOneTime() throws Exception {
        Task dishes = h.repeatingTask("Dishes", Recurrence.DAILY, true, h.ana, h.now.plusHours(2));
        OneTimeTask changed = new OneTimeTask("Dishes", null, 1, h.now.plusHours(2), h.ana.getId());
        changed.setId(dishes.getId());

        h.tasks().updateTask(changed);
    }

    @Test(expected = ValidationException.class)
    public void aMonthlyTaskCantStartOnThe31st() throws Exception {
        h.tasks().createTask(new com.settlr.model.RecurringTask("Rent day", null, 1,
                h.now.withMonth(1).withDayOfMonth(31).plusYears(1), h.ana.getId(), Recurrence.MONTHLY, false));
    }
}
