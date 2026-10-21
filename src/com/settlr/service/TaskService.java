package com.settlr.service;

import com.settlr.dao.MembershipDao;
import com.settlr.dao.SwapRequestDao;
import com.settlr.dao.TaskAssignmentDao;
import com.settlr.dao.TaskDao;
import com.settlr.exception.AuthException;
import com.settlr.exception.DataAccessException;
import com.settlr.exception.NotFoundException;
import com.settlr.exception.PermissionDeniedException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.ActivityType;
import com.settlr.model.Recurrence;
import com.settlr.model.RecurringTask;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;
import com.settlr.util.DBConnection;
import com.settlr.util.DateTimeUtil;
import com.settlr.util.Session;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Business rules for tasks: who may create, edit, archive and complete them,
 * and what happens to assignments when a task changes. Reads the current user
 * and household from Session and never contains SQL; the DAOs do that.
 */
public class TaskService {

    /** Used to mean "every pending assignment": no task is due before this. */
    private static final LocalDateTime EARLIEST = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final TaskDao taskDao = new TaskDao();
    private final TaskAssignmentDao assignmentDao = new TaskAssignmentDao();
    private final SwapRequestDao swapDao = new SwapRequestDao();
    private final MembershipDao membershipDao = new MembershipDao();
    private final ActivityService activityService = new ActivityService();

    /** One step of a transaction; gets the shared connection. */
    private interface TransactionBody {
        void run(Connection c) throws SettlrException;
    }

    // ---- public API (master doc 8.4) -----------------------------------

    /** Saves a new task and its first assignment together. */
    public Task createTask(Task task) throws SettlrException {
        int userId = currentUser().getId();
        int householdId = currentHouseholdId();
        validateTask(task, householdId);

        task.setHouseholdId(householdId);
        task.setCreatedBy(userId);
        runInTransaction(c -> {
            task.setId(taskDao.insert(c, task));
            assignmentDao.insertIfAbsent(c, new TaskAssignment(
                    task.getId(), task.getDefaultAssigneeId(), task.getFirstDueAt()));
        });
        return task;
    }

    /**
     * Changes a task's definition. Past assignments stay as they are; future
     * PENDING ones are replaced by the next one from the new definition, which
     * goes to the assignee chosen in the form (rotation continues from there).
     */
    public void updateTask(Task task) throws SettlrException {
        if (task == null) {
            throw new ValidationException("Task is required.");
        }
        Task existing = loadOwnTask(task.getId());
        requireCanModify(existing);
        validateTask(task, existing.getHouseholdId());
        if ((existing instanceof RecurringTask) != (task instanceof RecurringTask)) {
            throw new ValidationException("A task cannot change between one-time and recurring.");
        }

        LocalDateTime now = LocalDateTime.now();
        task.setArchived(false);
        runInTransaction(c -> {
            taskDao.update(c, task);
            swapDao.deleteByTaskAfter(c, task.getId(), now);
            assignmentDao.deletePendingAfter(c, task.getId(), now);
            LocalDateTime next = task.getNextDueDate(now); // polymorphic: depends on task type
            if (next != null) {
                assignmentDao.insertIfAbsent(c, new TaskAssignment(
                        task.getId(), task.getDefaultAssigneeId(), next));
            }
        });
    }

    /** "Deletes" a task: archives it and removes only its PENDING assignments (3.3). */
    public void archiveTask(int taskId) throws SettlrException {
        Task existing = loadOwnTask(taskId);
        requireCanModify(existing);

        runInTransaction(c -> {
            swapDao.deleteByTaskAfter(c, taskId, EARLIEST);
            assignmentDao.deletePendingAfter(c, taskId, EARLIEST);
            taskDao.delete(c, taskId);
        });
    }

    /** Marks an assignment done (or done late) and logs it to the activity feed. */
    public void completeAssignment(int assignmentId) throws SettlrException {
        int userId = currentUser().getId();
        TaskAssignment a = assignmentDao.findById(assignmentId);
        requireInHousehold(a);

        if (a.getAssigneeId() != userId && !Session.getInstance().isAdmin()) {
            throw new PermissionDeniedException("Only the assignee or an Admin can complete this task.");
        }
        if (a.getStatus() == TaskStatus.MISSED) {
            throw new ValidationException("This task was missed and can no longer be completed.");
        }
        if (a.getStatus() != TaskStatus.PENDING) {
            throw new ValidationException("This task is already completed.");
        }

        LocalDateTime now = LocalDateTime.now();
        a.markComplete(now);       // the DONE / DONE_LATE rule lives in the model
        assignmentDao.update(a);
        logCompletion(a, now);
    }

    /** The current user's assignments due today, in any status so the screen can show badges. */
    public List<TaskAssignment> getMyTasksDueToday() throws SettlrException {
        int userId = currentUser().getId();
        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<TaskAssignment> today = assignmentDao.findBetween(currentHouseholdId(), start, start.plusDays(1));
        return onlyAssignedTo(today, userId);
    }

    /** The current user's PENDING assignments whose due time has passed. */
    public List<TaskAssignment> getMyOverdueTasks() throws SettlrException {
        int userId = currentUser().getId();
        return onlyAssignedTo(assignmentDao.findPendingDueBefore(LocalDateTime.now()), userId);
    }

    /** Everyone's assignments for the Monday-to-Sunday week containing the given day. */
    public List<TaskAssignment> getWeek(LocalDate anyDayInWeek) throws SettlrException {
        if (anyDayInWeek == null) {
            throw new ValidationException("Please choose a day.");
        }
        int householdId = currentHouseholdId();
        LocalDateTime start = DateTimeUtil.weekStart(anyDayInWeek);
        return assignmentDao.findBetween(householdId, start, start.plusDays(7));
    }

    // ---- validation and permissions ------------------------------------

    private void validateTask(Task task, int householdId) throws SettlrException {
        if (task == null) {
            throw new ValidationException("Task is required.");
        }
        requireActiveMember(householdId, task.getDefaultAssigneeId());

        // Monthly tasks must start on day 1-28 so every month has that day
        // (same rule as recurring bills); Recurrence.next relies on it.
        if (task instanceof RecurringTask) {
            RecurringTask recurring = (RecurringTask) task;
            if (recurring.getRecurrence() == Recurrence.MONTHLY && task.getFirstDueAt().getDayOfMonth() > 28) {
                throw new ValidationException("Monthly tasks must start on day 1 to 28 of the month.");
            }
        }
    }

    private void requireActiveMember(int householdId, int userId) throws SettlrException {
        for (User member : membershipDao.findActiveMembers(householdId)) {
            if (member.getId() == userId) {
                return;
            }
        }
        throw new ValidationException("The assignee must be an active member of your household.");
    }

    /** Loads a non-archived task of the current household or throws NotFound. */
    private Task loadOwnTask(int taskId) throws SettlrException {
        Task task = taskDao.findById(taskId);
        if (task == null || task.isArchived() || task.getHouseholdId() != currentHouseholdId()) {
            throw new NotFoundException("That task could not be found.");
        }
        return task;
    }

    /** Only the creator or an Admin may change or delete a task (3.2). */
    private void requireCanModify(Task task) throws SettlrException {
        boolean creator = task.getCreatedBy() == currentUser().getId();
        if (!creator && !Session.getInstance().isAdmin()) {
            throw new PermissionDeniedException("Only the person who created this task or an Admin can change it.");
        }
    }

    /** Stops someone from touching another household's assignment by id. */
    private void requireInHousehold(TaskAssignment a) throws SettlrException {
        Task task = (a == null) ? null : taskDao.findById(a.getTaskId());
        if (task == null || task.getHouseholdId() != currentHouseholdId()) {
            throw new NotFoundException("That task could not be found.");
        }
    }

    // ---- session -------------------------------------------------------

    private User currentUser() throws SettlrException {
        Session session = Session.getInstance();
        if (!session.isLoggedIn()) {
            throw new AuthException("Please log in first.");
        }
        return session.getCurrentUser();
    }

    private int currentHouseholdId() throws SettlrException {
        currentUser();
        if (Session.getInstance().getCurrentHousehold() == null) {
            throw new PermissionDeniedException("Please create or join a household first.");
        }
        return Session.getInstance().getCurrentHousehold().getId();
    }

    // ---- helpers -------------------------------------------------------

    private List<TaskAssignment> onlyAssignedTo(List<TaskAssignment> all, int userId) {
        List<TaskAssignment> mine = new ArrayList<>();
        for (TaskAssignment a : all) {
            if (a.getAssigneeId() == userId) {
                mine.add(a);
            }
        }
        return mine;
    }

    /** Credits the assignee even when an Admin clicked, because rankings read assignee_id. */
    private void logCompletion(TaskAssignment a, LocalDateTime now) throws SettlrException {
        String message = a.getAssigneeName() + " completed " + a.getTaskTitle();
        ActivityType type = ActivityType.TASK_DONE;
        if (a.getStatus() == TaskStatus.DONE_LATE) {
            type = ActivityType.TASK_LATE;
            message += " (" + describeLateness(Duration.between(a.getDueAt(), now)) + " late)";
        }
        activityService.log(type, a.getAssigneeId(), message);
    }

    /** "5 minutes", "2 hours" or "3 days", rounded down (at least 1 minute). */
    private String describeLateness(Duration late) {
        long minutes = Math.max(1, late.toMinutes());
        if (minutes < 60) {
            return plural(minutes, "minute");
        }
        if (minutes < 60 * 24) {
            return plural(minutes / 60, "hour");
        }
        return plural(minutes / (60 * 24), "day");
    }

    private String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    /**
     * Runs several DAO calls as one all-or-nothing unit. The only JDBC in this
     * class: the service decides where a transaction starts and ends, the DAOs
     * still hold every SQL statement.
     */
    private void runInTransaction(TransactionBody body) throws SettlrException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            c.setAutoCommit(false);
            try {
                body.run(c);
                c.commit();
            } catch (SettlrException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save your changes. Please try again.");
        }
    }
}
