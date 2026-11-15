package com.settlr.service;

import com.settlr.model.Recurrence;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Checks what the app catches up on when it starts: missed turns, expired swaps, repeating tasks. */
public class CatchUpServiceTest {

    private TestHousehold h;

    /** Before each test: a fresh test household in memory (the catch-up runs before anyone logs in). */
    @Before
    public void setUp() throws Exception {
        h = new TestHousehold(); // the catch-up runs before anyone logs in
    }

    /** After each test: logs out. */
    @After
    public void tearDown() {
        h.logOut();
    }

    @Test
    public void aTurnMoreThanADayLateBecomesMissed() throws Exception {
        Task old = h.oneTimeTask("Water plants", 1, h.ben, h.now.minusDays(2));
        Task recent = h.oneTimeTask("Sweep", 1, h.ben, h.now.minusHours(5));

        h.catchUp().runOnStartup();

        assertEquals(TaskStatus.MISSED, h.onlyTurnOf(old).getStatus());
        assertEquals(TaskStatus.PENDING, h.onlyTurnOf(recent).getStatus()); // still time to do it late
    }

    @Test
    public void aRotatingDailyTaskGetsItsMissingTurnsInJoinOrder() throws Exception {
        Task dishes = h.repeatingTask("Dishes", Recurrence.DAILY, true, h.ana, h.now.minusDays(2).plusHours(1));

        h.catchUp().runOnStartup();

        List<TaskAssignment> turns = h.turnsOf(dishes);
        assertEquals(3, turns.size()); // -2 days (Ana), -1 day (Ben), +1 hour (Carlo)
        assertEquals(h.ben.getId(), turns.get(1).getAssigneeId());
        assertEquals(h.carlo.getId(), turns.get(2).getAssigneeId());
        assertTrue(turns.get(2).getDueAt().isAfter(h.now)); // stops at the first one still ahead
    }

    @Test
    public void aNonRotatingTaskStaysWithTheSamePerson() throws Exception {
        Task bathroom = h.repeatingTask("Bathroom", Recurrence.DAILY, false, h.carlo, h.now.minusDays(1).plusHours(1));

        h.catchUp().runOnStartup();

        for (TaskAssignment turn : h.turnsOf(bathroom)) {
            assertEquals(h.carlo.getId(), turn.getAssigneeId());
        }
    }

    @Test
    public void runningItTwiceChangesNothingTheSecondTime() throws Exception {
        h.repeatingTask("Dishes", Recurrence.DAILY, true, h.ana, h.now.minusDays(2).plusHours(1));
        h.oneTimeTask("Water plants", 1, h.ben, h.now.minusDays(2));

        h.catchUp().runOnStartup();
        List<String> first = snapshot();
        h.catchUp().runOnStartup();

        assertEquals(first, snapshot());
    }

    @Test
    public void aSwapNobodyAnsweredBeforeTheDeadlineIsDeclined() throws Exception {
        Task trash = h.oneTimeTask("Trash", 1, h.ben, h.now.minusHours(2));
        SwapRequest swap = new SwapRequest(h.onlyTurnOf(trash).getId(), h.ben.getId(), h.ana.getId());
        h.db.swaps().insert(swap);

        h.catchUp().runOnStartup();

        assertEquals(SwapStatus.DECLINED, h.db.swaps().findById(swap.getId()).getStatus());
    }

    /** Every turn as "task, who, when, status", to compare two runs. */
    private List<String> snapshot() throws Exception {
        List<String> rows = new ArrayList<>();
        for (TaskAssignment a : h.db.assignments().findBetween(h.household.getId(),
                h.now.minusYears(1), h.now.plusYears(1))) {
            rows.add(a.getTaskId() + " " + a.getAssigneeId() + " " + a.getDueAt() + " " + a.getStatus());
        }
        return rows;
    }
}
