package com.settlr.service;

import com.settlr.exception.PermissionDeniedException;
import com.settlr.exception.ValidationException;
import com.settlr.model.Role;
import com.settlr.model.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

/** Checks the Admin-only actions and the "always keep one Admin" rule. */
public class HouseholdServiceTest {

    private TestHousehold h;

    /** Before each test: a fresh test household in memory (nobody logged in yet). */
    @Before
    public void setUp() throws Exception {
        h = new TestHousehold();
    }

    /** After each test: logs out. */
    @After
    public void tearDown() {
        h.logOut();
    }

    @Test(expected = PermissionDeniedException.class)
    public void aMemberCantRenameTheHousehold() throws Exception {
        h.logIn(h.ben);
        h.households().rename("Ben's Place");
    }

    @Test(expected = PermissionDeniedException.class)
    public void aMemberCantRemoveSomeone() throws Exception {
        h.logIn(h.ben);
        h.households().removeMember(h.carlo.getId());
    }

    @Test
    public void anAdminCanRenameTheHousehold() throws Exception {
        h.logIn(h.ana);
        h.households().rename("Casa Nueva");

        assertEquals("Casa Nueva", h.db.households().findById(h.household.getId()).getName());
    }

    @Test
    public void removingSomeoneHandsOverTheirTurnsAndChangesTheInviteCode() throws Exception {
        Task wifi = h.oneTimeTask("Fix wifi", 3, h.carlo, h.now.plusDays(2));
        h.logIn(h.ana);

        String newCode = h.households().removeMember(h.carlo.getId());

        assertFalse(h.db.memberships().find(h.household.getId(), h.carlo.getId()).isActive());
        assertEquals(h.ana.getId(), h.onlyTurnOf(wifi).getAssigneeId()); // to the Admin who removed him
        assertNotEquals("TESTAB", newCode);
    }

    @Test(expected = ValidationException.class)
    public void theOnlyAdminCantStepDown() throws Exception {
        h.logIn(h.ana);
        h.households().setRole(h.ana.getId(), Role.MEMBER);
    }

    @Test(expected = ValidationException.class)
    public void theOnlyAdminCantLeaveWhileOthersAreStillIn() throws Exception {
        h.logIn(h.ana);
        h.households().leave();
    }

    @Test
    public void afterMakingSomeoneElseAdminTheFirstAdminCanStepDown() throws Exception {
        h.logIn(h.ana);
        h.households().setRole(h.ben.getId(), Role.ADMIN);
        h.households().setRole(h.ana.getId(), Role.MEMBER);

        assertEquals(Role.MEMBER, h.db.memberships().find(h.household.getId(), h.ana.getId()).getRole());
    }
}
