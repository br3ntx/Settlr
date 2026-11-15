package com.settlr.service;

import com.settlr.dao.memory.InMemoryDaoFactory;
import com.settlr.model.Household;
import com.settlr.model.Membership;
import com.settlr.model.OneTimeTask;
import com.settlr.model.Recurrence;
import com.settlr.model.RecurringTask;
import com.settlr.model.Role;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.User;
import com.settlr.util.PasswordHasher;
import com.settlr.util.Session;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A ready-made household for the service tests, kept in the in-memory DAOs:
 * Ana (Admin), Ben and Carlo, who joined in that order. Services are made with
 * these DAOs through their constructors (dependency injection), so no MySQL is needed.
 */
class TestHousehold {

    static final String PASSWORD = "settlr123";
    /** Hashed once: PBKDF2 is slow on purpose, so the tests share one hash. */
    private static final String HASH = PasswordHasher.hash(PASSWORD);

    final InMemoryDaoFactory db = new InMemoryDaoFactory();
    final Household household = new Household(1, "Casa Test", "TESTAB", 1);
    final User ana = user(1, "ana", "Ana");
    final User ben = user(2, "ben", "Ben");
    final User carlo = user(3, "carlo", "Carlo");
    final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

    TestHousehold() throws Exception {
        db.households().insert(household);
        int minutesAgo = 300;
        for (User u : List.of(ana, ben, carlo)) {
            db.users().insert(u);
            db.memberships().insert(new Membership(household.getId(), u.getId(),
                    u == ana ? Role.ADMIN : Role.MEMBER, now.minusMinutes(minutesAgo--), true));
        }
    }

    /** Logs this person in, with the role they have in the household. */
    void logIn(User u) {
        Session.getInstance().start(u, household, u == ana ? Role.ADMIN : Role.MEMBER);
    }

    /** Ends the Session. */
    void logOut() {
        Session.getInstance().end();
    }

    /** A TaskService on these in-memory DAOs. */
    TaskService tasks() { return new TaskService(db, Session.getInstance()); }

    /** A SwapService on these in-memory DAOs. */
    SwapService swaps() { return new SwapService(db, Session.getInstance()); }

    /** A HouseholdService on these in-memory DAOs. */
    HouseholdService households() { return new HouseholdService(db, Session.getInstance()); }

    /** A CatchUpService on these in-memory DAOs. */
    CatchUpService catchUp() { return new CatchUpService(db, Session.getInstance()); }

    /** A ShopService on these in-memory DAOs. */
    ShopService shop() { return new ShopService(db, Session.getInstance()); }

    /** An AuthService on these in-memory DAOs. */
    AuthService auth() { return new AuthService(db, Session.getInstance()); }

    /** A one-time task for this person, due at the given time, saved with its one turn. */
    Task oneTimeTask(String title, int points, User assignee, LocalDateTime due) throws Exception {
        return saveTask(new OneTimeTask(title, null, points, due, assignee.getId()));
    }

    /** A repeating task starting with this person's turn, saved with that first turn. */
    Task repeatingTask(String title, Recurrence how, boolean rotates, User first, LocalDateTime due)
            throws Exception {
        return saveTask(new RecurringTask(title, null, 1, due, first.getId(), how, rotates));
    }

    /** Saves a task straight into the DAOs (like createTask, but allowed in the past). */
    private Task saveTask(Task task) throws Exception {
        task.setHouseholdId(household.getId());
        task.setCreatedBy(ana.getId());
        db.tasks().insert(task);
        db.assignments().insert(new TaskAssignment(task.getId(), task.getDefaultAssigneeId(), task.getFirstDueAt()));
        return task;
    }

    /** Every turn of a task, oldest first. */
    List<TaskAssignment> turnsOf(Task task) throws Exception {
        List<TaskAssignment> turns = db.assignments().findBetween(household.getId(),
                now.minusYears(1), now.plusYears(1));
        turns.removeIf(a -> a.getTaskId() != task.getId());
        return turns;
    }

    /** The one turn of a task (for one-time tasks). */
    TaskAssignment onlyTurnOf(Task task) throws Exception {
        List<TaskAssignment> turns = turnsOf(task);
        if (turns.size() != 1) {
            throw new AssertionError("Expected one turn of " + task.getTitle() + ", found " + turns.size());
        }
        return turns.get(0);
    }

    /** A member with the shared test password. */
    private User user(int id, String username, String name) {
        return new User(id, username, name, HASH, LocalDateTime.now().minusDays(10));
    }
}
