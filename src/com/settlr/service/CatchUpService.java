package com.settlr.service;

import com.settlr.dao.DaoFactory;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.SwapRequestDao;
import com.settlr.dao.TaskAssignmentDao;
import com.settlr.dao.TaskDao;
import com.settlr.exception.SettlrException;
import com.settlr.model.ActivityType;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;
import com.settlr.util.Session;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The startup routine (master doc 3.7). Nothing runs while the app is closed,
 * so on every start this catches up on what was missed: it marks overdue
 * tasks, declines expired swaps, creates due recurring assignments and asks
 * ExpenseService for due bills. It runs before login, so it never uses Session.
 *
 * Every step is safe to repeat: running it twice in a row changes nothing the
 * second time.
 */
public class CatchUpService {

    /** A PENDING assignment this long past its due time becomes MISSED (3.3). */
    private static final int MISSED_AFTER_HOURS = 24;

    private final TaskDao taskDao;
    private final TaskAssignmentDao assignmentDao;
    private final SwapRequestDao swapDao;
    private final MembershipDao membershipDao;
    private final ActivityService activityService;
    private final ExpenseService expenseService;

    /** Uses the app's MySQL DAOs and the logged-in Session. */
    public CatchUpService() {
        this(DaoFactory.get(), Session.getInstance());
    }

    /**
     * Everything this service needs is passed in here (dependency injection), so a test can
     * give it in-memory DAOs instead of MySQL.
     */
    public CatchUpService(DaoFactory daos, Session session) {
        this.taskDao = daos.tasks();
        this.assignmentDao = daos.assignments();
        this.swapDao = daos.swaps();
        this.membershipDao = daos.memberships();
        this.activityService = new ActivityService(daos, session);
        this.expenseService = new ExpenseService(daos, session);
    }

    /** Runs the four catch-up steps in the order required by 3.7. */
    public void runOnStartup() throws SettlrException {
        LocalDateTime now = LocalDateTime.now();
        markMissedAssignments(now);
        declineExpiredSwaps(now);
        createRecurringAssignments(now);
        expenseService.generateDueBills(LocalDate.now());
    }

    // ---- step 1 --------------------------------------------------------

    /** PENDING for 24 hours past due: set MISSED and log it. */
    private void markMissedAssignments(LocalDateTime now) throws SettlrException {
        LocalDateTime cutoff = now.minusHours(MISSED_AFTER_HOURS);
        for (TaskAssignment a : assignmentDao.findPendingDueBefore(cutoff)) {
            a.setStatus(TaskStatus.MISSED);
            assignmentDao.update(a);
            // Logged after the update: if logging fails, a rerun will not log
            // twice because the row is no longer PENDING.
            activityService.log(ActivityType.TASK_MISSED, a.getAssigneeId(),
                    a.getAssigneeName() + " missed " + a.getTaskTitle());
        }
    }

    // ---- step 2 --------------------------------------------------------

    /** Swaps still PENDING once their assignment is past due can never be accepted: decline them. */
    private void declineExpiredSwaps(LocalDateTime now) throws SettlrException {
        for (SwapRequest swap : swapDao.findPendingPastDue(now)) {
            swap.setStatus(SwapStatus.DECLINED);
            swap.setRespondedAt(now);
            swapDao.update(swap);
        }
    }

    // ---- step 3 --------------------------------------------------------

    private void createRecurringAssignments(LocalDateTime now) throws SettlrException {
        for (Task task : taskDao.findAllActive()) {
            if (task.repeats()) {
                catchUpTask(task, now);
            }
        }
    }

    /**
     * Creates every assignment from the task's latest one up to and including
     * the first one still in the future. If the latest is already in the
     * future there is nothing to do, which is what makes a second run harmless.
     * Who gets each turn is up to the task itself (Task.nextAssignee).
     */
    private void catchUpTask(Task task, LocalDateTime now) throws SettlrException {
        TaskAssignment latest = assignmentDao.findLatestForTask(task.getId());
        List<User> members = membershipDao.findActiveMembers(task.getHouseholdId());
        if (latest == null || members.isEmpty()) {
            return; // createTask always makes a first assignment; nobody to give it to otherwise
        }

        LocalDateTime cursor = latest.getDueAt();
        int previousAssignee = latest.getAssigneeId();
        while (!cursor.isAfter(now)) {
            cursor = task.getNextDueDate(cursor);
            if (cursor == null) {
                return; // no more due dates
            }
            previousAssignee = task.nextAssignee(previousAssignee, members);
            // insertIfAbsent, because UNIQUE (task_id, due_at) must never be violated.
            assignmentDao.insertIfAbsent(new TaskAssignment(task.getId(), previousAssignee, cursor));
        }
    }
}
