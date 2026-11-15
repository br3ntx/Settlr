package com.settlr.service;

import com.settlr.exception.PermissionDeniedException;
import com.settlr.exception.ValidationException;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Checks swap requests: who may ask, who may answer, and what accepting changes. */
public class SwapServiceTest {

    private TestHousehold h;
    private TaskAssignment anasTurn;

    /** Before each test: a fresh test household in memory, a turn of Ana's due tomorrow, and Ana logged in. */
    @Before
    public void setUp() throws Exception {
        h = new TestHousehold();
        Task mop = h.oneTimeTask("Mop floors", 3, h.ana, h.now.plusDays(1));
        anasTurn = h.onlyTurnOf(mop);
        h.logIn(h.ana);
    }

    /** After each test: logs out. */
    @After
    public void tearDown() {
        h.logOut();
    }

    @Test
    public void acceptingASwapGivesTheTurnToThePersonAsked() throws Exception {
        h.swaps().request(anasTurn.getId(), h.ben.getId());

        h.logIn(h.ben);
        SwapRequest swap = h.swaps().getIncoming().get(0);
        h.swaps().accept(swap.getId());

        assertEquals(h.ben.getId(), h.db.assignments().findById(anasTurn.getId()).getAssigneeId());
        assertEquals(SwapStatus.ACCEPTED, h.db.swaps().findById(swap.getId()).getStatus());
    }

    @Test
    public void decliningLeavesTheTurnWhereItWas() throws Exception {
        h.swaps().request(anasTurn.getId(), h.ben.getId());

        h.logIn(h.ben);
        SwapRequest swap = h.swaps().getIncoming().get(0);
        h.swaps().decline(swap.getId());

        assertEquals(h.ana.getId(), h.db.assignments().findById(anasTurn.getId()).getAssigneeId());
        assertEquals(SwapStatus.DECLINED, h.db.swaps().findById(swap.getId()).getStatus());
    }

    @Test(expected = PermissionDeniedException.class)
    public void onlyThePersonWhoseTurnItIsCanAskForASwap() throws Exception {
        h.logIn(h.carlo);
        h.swaps().request(anasTurn.getId(), h.ben.getId());
    }

    @Test(expected = PermissionDeniedException.class)
    public void onlyThePersonAskedCanAnswer() throws Exception {
        h.swaps().request(anasTurn.getId(), h.ben.getId());
        int swapId = h.swaps().getOutgoing().get(0).getId();

        h.logIn(h.carlo);
        h.swaps().accept(swapId);
    }

    @Test(expected = ValidationException.class)
    public void aTurnCanOnlyHaveOneSwapRequestWaiting() throws Exception {
        h.swaps().request(anasTurn.getId(), h.ben.getId());
        h.swaps().request(anasTurn.getId(), h.carlo.getId());
    }
}
