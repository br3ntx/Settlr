package com.settlr.service;

import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;
import com.settlr.model.ExpenseShare;
import com.settlr.model.OwnedPet;
import com.settlr.model.Task;
import com.settlr.model.User;
import com.settlr.split.EqualSplit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the pet belongs to the whole household: everyone sees the same pet, any member
 * can change it, and its mood depends on everyone's tasks and bills.
 */
public class HouseholdPetTest {

    private TestHousehold h;

    /** Before each test: the test household owns a Dog, with Ana logged in. */
    @Before
    public void setUp() throws Exception {
        h = new TestHousehold();
        h.logIn(h.ana);
        h.db.shop().insertOwned(null, h.household.getId(), "PET_DOG");
    }

    /** After each test: logs out. */
    @After
    public void tearDown() {
        h.logOut();
    }

    @Test
    public void everyMemberSeesTheSamePet() throws Exception {
        h.logIn(h.carlo);
        List<OwnedPet> pets = h.pets().getPets();

        assertEquals(1, pets.size());
        assertEquals("PET_DOG", h.pets().getShownPetCode(pets));
    }

    @Test
    public void anyMemberCanRenameThePetAndEveryoneSeesIt() throws Exception {
        h.logIn(h.ben); // a Member, not the Admin
        OwnedPet dog = h.pets().getPets().get(0);
        dog.setName("Mochi");
        h.pets().save("PET_DOG", List.of(dog));

        h.logIn(h.ana);
        assertEquals("Mochi", h.pets().getPets().get(0).getName());
    }

    @Test
    public void happyWhenEveryoneIsCaughtUp() throws Exception {
        PetService.Mood mood = h.pets().getMood();

        assertTrue(mood.isHappy());
        assertEquals("everyone's caught up", mood.getReason());
    }

    @Test
    public void sadWhenSomeoneElseHasAnOverdueTask() throws Exception {
        h.oneTimeTask("Trash", 1, h.ben, h.now.minusHours(3));
        h.oneTimeTask("Dishes", 1, h.ben, h.now.minusHours(2));

        PetService.Mood mood = h.pets().getMood();

        assertFalse(mood.isHappy());
        assertEquals("Ben has 2 overdue tasks", mood.getReason());
    }

    @Test
    public void saysYouWhenItsYourTask() throws Exception {
        h.oneTimeTask("Trash", 1, h.ana, h.now.minusHours(3));

        assertEquals("you have 1 overdue task", h.pets().getMood().getReason());
    }

    @Test
    public void namesEveryoneWhoIsLate() throws Exception {
        h.oneTimeTask("Trash", 1, h.carlo, h.now.minusHours(3));
        h.oneTimeTask("Dishes", 1, h.ana, h.now.minusHours(2));

        assertEquals("you and Carlo have overdue tasks", h.pets().getMood().getReason());
    }

    @Test
    public void happyAgainOnceTheTaskIsDone() throws Exception {
        Task trash = h.oneTimeTask("Trash", 1, h.ben, h.now.minusHours(3));
        h.tasks().completeAssignment(h.onlyTurnOf(trash).getId()); // Ana is Admin, so she can tick it

        assertTrue(h.pets().getMood().isHappy());
    }

    @Test
    public void sadWhenSomeoneOwesABillPastItsDueDate() throws Exception {
        addExpense("Electricity", "300.00", h.ana, LocalDate.now().minusDays(2)); // Ben and Carlo owe Ana

        PetService.Mood mood = h.pets().getMood();

        assertFalse(mood.isHappy());
        assertEquals("Ben and Carlo owe bills that are past due", mood.getReason());
    }

    @Test
    public void aBillThatIsntDueYetDoesntMatter() throws Exception {
        addExpense("Groceries", "300.00", h.ana, LocalDate.now().plusDays(3));

        assertTrue(h.pets().getMood().isHappy());
    }

    @Test
    public void overdueTasksAreNamedBeforeBills() throws Exception {
        addExpense("Electricity", "300.00", h.ana, LocalDate.now().minusDays(2));
        h.oneTimeTask("Trash", 1, h.carlo, h.now.minusHours(3));

        assertEquals("Carlo has 1 overdue task", h.pets().getMood().getReason());
    }

    /** An expense split equally among all three, paid by payer. */
    private void addExpense(String title, String amount, User payer, LocalDate due) throws Exception {
        Expense e = Expense.builder().household(h.household.getId()).title(title).category(ExpenseCategory.OTHER)
                .amount(new BigDecimal(amount)).paidBy(payer.getId()).dueDate(due).createdBy(payer.getId()).build();
        int expenseId = h.db.expenses().insert(e);
        for (ExpenseShare s : new EqualSplit().split(e.getAmount(), payer.getId(),
                Arrays.asList(h.ana.getId(), h.ben.getId(), h.carlo.getId()))) {
            s.setExpenseId(expenseId);
            h.db.shares().insert(s);
        }
    }
}
