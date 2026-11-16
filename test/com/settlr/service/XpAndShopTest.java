package com.settlr.service;

import com.settlr.exception.ValidationException;
import com.settlr.model.Household;
import com.settlr.model.Membership;
import com.settlr.model.Role;
import com.settlr.model.Task;
import com.settlr.model.User;
import com.settlr.model.XpActivity;
import com.settlr.util.Session;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks the XP rules (XpRules) and that the Shop spends XP correctly. XP and items belong to
 * the whole household, so what one member earns or buys counts for everyone.
 */
public class XpAndShopTest {

    private TestHousehold h;

    /** Before each test: a fresh test household in memory, with Ana logged in and no XP yet. */
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
    public void onTimePointsAreWorth10LatePoints5AndPayments20() {
        assertEquals(3 * 10 + 2 * 5 + 1 * 20, XpRules.earned(new XpActivity(3, 2, 1)));
        assertEquals(0, XpRules.earned(new XpActivity(0, 0, 0)));
    }

    @Test
    public void theBalanceIsWhatYouEarnedPlusRefundsMinusWhatYouSpent() {
        assertEquals(60 - 150 + 75, XpRules.balance(new XpActivity(6, 0, 0), -150 + 75));
    }

    @Test
    public void finishingTasksEarnsXpInTheShop() throws Exception {
        Task onTime = h.oneTimeTask("Mop", 3, h.ana, h.now.plusHours(2));
        Task late = h.oneTimeTask("Dishes", 2, h.ana, h.now.minusHours(2));
        h.tasks().completeAssignment(h.onlyTurnOf(onTime).getId());
        h.tasks().completeAssignment(h.onlyTurnOf(late).getId());

        assertEquals(3 * 10 + 2 * 5, h.shop().getBalance());
    }

    @Test
    public void buyingTakesThePriceOff() throws Exception {
        earn(10); // 10 points on time = 100 XP

        int left = h.shop().buy("HAT_PARTY"); // costs 60

        assertEquals(40, left);
        assertEquals(40, h.shop().getBalance());
        assertTrue(h.shop().getOwnedCodes().contains("HAT_PARTY"));
    }

    @Test(expected = ValidationException.class)
    public void youCantBuyWhatYouCantAfford() throws Exception {
        earn(5); // 50 XP
        h.shop().buy("HAT_CROWN"); // costs 300
    }

    @Test(expected = ValidationException.class)
    public void youCantBuyTheSameThingTwice() throws Exception {
        earn(20);
        h.shop().buy("HAT_BOW");
        h.shop().buy("HAT_BOW");
    }

    @Test
    public void everyonesTasksAddToOneHouseholdBalance() throws Exception {
        earn(4, h.ana);   // 40 XP
        earn(3, h.ben);   // 30 XP
        earn(2, h.carlo); // 20 XP

        assertEquals(90, h.shop().getBalance()); // Ana sees everyone's XP
        h.logIn(h.ben);
        assertEquals(90, h.shop().getBalance()); // and so does Ben
    }

    @Test
    public void whatOneMemberBuysEveryoneOwnsAndPaysFor() throws Exception {
        earn(10, h.ben); // Ben earns 100 XP

        h.shop().buy("HAT_PARTY"); // Ana spends 60 of it

        h.logIn(h.carlo);
        assertTrue(h.shop().getOwnedCodes().contains("HAT_PARTY"));
        assertEquals(40, h.shop().getBalance());
    }

    @Test(expected = ValidationException.class)
    public void aSecondMemberCantBuyWhatTheHouseholdAlreadyHas() throws Exception {
        earn(20);
        h.shop().buy("HAT_BOW");
        h.logIn(h.ben);
        h.shop().buy("HAT_BOW");
    }

    @Test
    public void anotherHouseholdsXpAndItemsDontCount() throws Exception {
        earn(10);
        h.shop().buy("HAT_PARTY");

        // Dani has a household of her own in the same database
        User dani = new User(4, "dani", "Dani", h.ana.getPasswordHash(), h.now);
        h.db.users().insert(dani);
        Household casaOtra = new Household(2, "Casa Otra", "PTHRAZ", dani.getId());
        h.db.households().insert(casaOtra);
        h.db.memberships().insert(new Membership(casaOtra.getId(), dani.getId(), Role.ADMIN, h.now, true));
        Session.getInstance().start(dani, casaOtra, Role.ADMIN);

        assertEquals(0, h.shop().getBalance());
        assertTrue(h.shop().getOwnedCodes().isEmpty());
    }

    /** Finishes tasks on time worth this many points, as Ana. */
    private void earn(int points) throws Exception {
        earn(points, h.ana);
    }

    /** Finishes tasks on time worth this many points, as this member. */
    private void earn(int points, User who) throws Exception {
        while (points > 0) {
            int p = Math.min(points, 5);
            Task t = h.oneTimeTask("Chore " + points, p, who, h.now.plusHours(1));
            h.tasks().completeAssignment(h.onlyTurnOf(t).getId());
            points -= p;
        }
    }
}
