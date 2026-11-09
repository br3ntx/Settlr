package com.settlr.service;

import com.settlr.dao.DaoFactory;
import com.settlr.dao.memory.InMemoryDaoFactory;
import com.settlr.dao.mysql.MySqlDaoFactory;
import com.settlr.model.Balance;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Household;
import com.settlr.model.Membership;
import com.settlr.model.Role;
import com.settlr.model.User;
import com.settlr.split.EqualSplit;
import com.settlr.util.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Checks who owes whom after netting. Runs on the in-memory DAOs (InMemoryDaoFactory), so no
 * MySQL is needed: the same BalanceService code, a different family of DAOs.
 */
public class BalanceServiceTest {

    private static final int HOUSEHOLD = 1;
    private final User ana = user(1, "Ana");
    private final User ben = user(2, "Ben");
    private final User carlo = user(3, "Carlo");
    private InMemoryDaoFactory db;

    @Before
    public void setUp() throws Exception {
        db = new InMemoryDaoFactory();
        DaoFactory.use(db);
        for (User u : Arrays.asList(ana, ben, carlo)) {
            db.users().insert(u);
            db.memberships().insert(new Membership(HOUSEHOLD, u.getId(), Role.MEMBER, LocalDateTime.now(), true));
        }
        logIn(ana);
    }

    @After
    public void tearDown() {
        Session.getInstance().end();
        DaoFactory.use(new MySqlDaoFactory()); // back to normal for any other test
    }

    @Test
    public void whatTheyOweMeMinusWhatIOweThem() throws Exception {
        addExpense(HOUSEHOLD, "Pizza", "300.00", ana, ana, ben, carlo); // Ben and Carlo owe Ana 100 each
        addExpense(HOUSEHOLD, "Water", "90.00", ben, ana, ben, carlo);  // Ana and Carlo owe Ben 30 each

        BalanceService balances = new BalanceService();

        assertEquals(money("70.00"), balanceWith(balances.getMyBalances(), ben));   // 100 - 30
        assertEquals(money("100.00"), balanceWith(balances.getMyBalances(), carlo));
        assertEquals(money("170.00"), balances.getTotalOwedToMe());
        assertEquals(money("0"), balances.getTotalIOwe());
    }

    @Test
    public void theOtherPersonSeesTheOppositeAmount() throws Exception {
        addExpense(HOUSEHOLD, "Pizza", "300.00", ana, ana, ben, carlo);
        addExpense(HOUSEHOLD, "Water", "90.00", ben, ana, ben, carlo);

        logIn(ben);
        BalanceService balances = new BalanceService();

        assertEquals(money("-70.00"), balanceWith(balances.getMyBalances(), ana));
        assertEquals(money("30.00"), balanceWith(balances.getMyBalances(), carlo));
        assertEquals(money("70.00"), balances.getTotalIOwe());
    }

    @Test
    public void paidSharesDontCount() throws Exception {
        List<ExpenseShare> shares = addExpense(HOUSEHOLD, "Pizza", "300.00", ana, ana, ben, carlo);
        ExpenseShare bensShare = shares.get(1);
        bensShare.setSettledAt(LocalDateTime.now()); // Ben paid his part
        db.shares().update(bensShare);

        BalanceService balances = new BalanceService();

        assertEquals(money("0"), balanceWith(balances.getMyBalances(), ben));
        assertEquals(money("100.00"), balances.getTotalOwedToMe()); // only Carlo now
    }

    @Test
    public void expensesOfAnotherHouseholdDontCount() throws Exception {
        addExpense(2, "Someone else's rent", "500.00", ana, ana, ben);

        assertEquals(money("0"), new BalanceService().getTotalOwedToMe());
    }

    @Test
    public void oddCentavosStillAddUp() throws Exception {
        addExpense(HOUSEHOLD, "Snacks", "100.00", ana, ana, ben, carlo); // 33.34 / 33.33 / 33.33

        BalanceService balances = new BalanceService();

        assertEquals(money("33.33"), balanceWith(balances.getMyBalances(), ben));
        assertEquals(money("66.66"), balances.getTotalOwedToMe()); // Ana's own 33.34 is already paid
    }

    // ---------- helpers ----------

    /** Saves an expense and its equal split straight into the in-memory DAOs. */
    private List<ExpenseShare> addExpense(int householdId, String title, String amount, User paidBy, User... among)
            throws Exception {
        Expense e = Expense.builder().household(householdId).title(title).category(ExpenseCategory.RENT)
                .amount(money(amount)).paidBy(paidBy.getId()).dueDate(LocalDate.now().plusDays(7))
                .createdBy(paidBy.getId()).build();
        int expenseId = db.expenses().insert(e);
        Integer[] ids = new Integer[among.length];
        for (int i = 0; i < among.length; i++) {
            ids[i] = among[i].getId();
        }
        List<ExpenseShare> shares = new EqualSplit().split(e.getAmount(), paidBy.getId(), Arrays.asList(ids));
        for (ExpenseShare s : shares) {
            s.setExpenseId(expenseId);
            db.shares().insert(s);
        }
        return shares;
    }

    private static BigDecimal balanceWith(List<Balance> balances, User other) {
        for (Balance b : balances) {
            if (b.getOtherUser().getId() == other.getId()) {
                return b.getAmount();
            }
        }
        throw new AssertionError("No balance with " + other.getDisplayName());
    }

    private static void logIn(User u) {
        Session.getInstance().start(u, new Household(HOUSEHOLD, "Casa Test", "TESTAB", 1), Role.MEMBER);
    }

    private static User user(int id, String name) {
        return new User(id, name.toLowerCase(), name, "0".repeat(64), LocalDateTime.now()); // a dummy password hash
    }

    /** Compares by value, so 70.00 and 70.0 are the same amount. */
    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static void assertEquals(BigDecimal expected, BigDecimal actual) {
        org.junit.Assert.assertEquals("expected " + expected + " but was " + actual, 0, expected.compareTo(actual));
    }
}
