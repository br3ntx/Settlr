package com.settlr.service;

import com.settlr.dao.DaoFactory;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.SwapRequestDao;
import com.settlr.dao.TaskAssignmentDao;
import com.settlr.dao.TaskDao;
import com.settlr.exception.NotFoundException;
import com.settlr.exception.PermissionDeniedException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.ActivityType;
import com.settlr.model.Recurrence;
import com.settlr.model.RecurringTask;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;
import com.settlr.util.Transaction;
import com.settlr.util.DateTimeUtil;
import com.settlr.util.Session;

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

    private final Session session = Session.getInstance();

    private static final String SAVE_FAILED = "Could not save your changes. Please try again.";

    /** Used to mean "every pending assignment": no task is due before this. */
    private static final LocalDateTime EARLIEST = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final TaskDao taskDao = DaoFactory.get().tasks();
    private final TaskAssignmentDao assignmentDao = DaoFactory.get().assignments();
    private final SwapRequestDao swapDao = DaoFactory.get().swaps();
    private final MembershipDao membershipDao = DaoFactory.get().memberships();
    private final ActivityService activityService = new ActivityService();

    /** One upcoming turn of a task: who does it and when. Read-only. */
    public static final class Turn {
        private final User user;
        private final LocalDateTime dueAt;

        Turn(User user, LocalDateTime dueAt) {
            this.user = user;
            this.dueAt = dueAt;
        }

        public User getUser() { return user; }
        public LocalDateTime getDueAt() { return dueAt; }
    }

    // ---- public API (master doc 8.4) -----------------------------------

    /** Saves a new task and its first assignment together. */
    public Task createTask(Task task) throws SettlrException {
        int userId = session.requireUser().getId();
        int householdId = session.requireHouseholdId();
        validateTask(task, householdId);

        task.setHouseholdId(householdId);
        task.setCreatedBy(userId);
        Transaction.run(SAVE_FAILED, c -> {
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
        Transaction.run(SAVE_FAILED, c -> {
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

        Transaction.run(SAVE_FAILED, c -> {
            swapDao.deleteByTaskAfter(c, taskId, EARLIEST);
            assignmentDao.deletePendingAfter(c, taskId, EARLIEST);
            taskDao.delete(c, taskId);
        });
    }

    /** Marks an assignment done (or done late) and logs it to the activity feed. */
    public void completeAssignment(int assignmentId) throws SettlrException {
        int userId = session.requireUser().getId();
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
        SwapRequest swap = swapDao.findPendingForAssignment(assignmentId);
        Transaction.run(SAVE_FAILED, c -> {
            assignmentDao.update(c, a);
            if (swap != null) {        // nobody can take a finished task, so its swap request ends too
                swap.setStatus(SwapStatus.DECLINED);
                swap.setRespondedAt(now);
                swapDao.update(c, swap);
            }
        });
        logCompletion(a, now);
    }

    /** The current user's assignments due today, in any status so the screen can show badges. */
    public List<TaskAssignment> getMyTasksDueToday() throws SettlrException {
        int userId = session.requireUser().getId();
        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<TaskAssignment> today = assignmentDao.findBetween(session.requireHouseholdId(), start, start.plusDays(1));
        return onlyAssignedTo(today, userId);
    }

    /** The current user's PENDING assignments whose due time has passed. */
    public List<TaskAssignment> getMyOverdueTasks() throws SettlrException {
        int userId = session.requireUser().getId();
        return onlyAssignedTo(assignmentDao.findPendingDueBefore(LocalDateTime.now()), userId);
    }

    /** Everyone's assignments for the Monday-to-Sunday week containing the given day. */
    public List<TaskAssignment> getWeek(LocalDate anyDayInWeek) throws SettlrException {
        if (anyDayInWeek == null) {
            throw new ValidationException("Please choose a day.");
        }
        int householdId = session.requireHouseholdId();
        LocalDateTime start = DateTimeUtil.weekStart(anyDayInWeek);
        return assignmentDao.findBetween(householdId, start, start.plusDays(7));
    }

    // ---- reading for the Tasks page ------------------------------------

    /** Every task of the household that isn't archived (for repeat, rotation and description details). */
    public List<Task> getActiveTasks() throws SettlrException {
        return taskDao.findActiveByHousehold(session.requireHouseholdId());
    }

    /** Everyone's PENDING assignments in this household whose due time has passed, earliest first. */
    public List<TaskAssignment> getOverdue() throws SettlrException {
        int householdId = session.requireHouseholdId();
        List<Integer> ids = new ArrayList<>();
        for (Task t : taskDao.findActiveByHousehold(householdId)) {
            ids.add(t.getId());
        }
        List<TaskAssignment> result = new ArrayList<>();
        for (TaskAssignment a : assignmentDao.findPendingDueBefore(LocalDateTime.now())) {
            if (ids.contains(a.getTaskId())) {
                result.add(a);
            }
        }
        return result;
    }

    /**
     * This assignment's turn and the next ones after it, e.g. You (today), Carlo (Mon), Dani (Tue).
     * Later turns are a preview: the app only creates them when they come up (CatchUpService),
     * using the same rotation rule as here. A one-time task has just the one turn.
     */
    public List<Turn> upcomingTurns(TaskAssignment from, int count) throws SettlrException {
        requireInHousehold(from);
        Task task = taskDao.findById(from.getTaskId());
        List<User> members = membershipDao.findActiveMembers(session.requireHouseholdId());
        List<Turn> turns = new ArrayList<>();
        int assignee = from.getAssigneeId();
        LocalDateTime due = from.getDueAt();
        turns.add(new Turn(findMember(members, assignee, from.getAssigneeName()), due));
        if (!(task instanceof RecurringTask) || members.isEmpty()) {
            return turns;
        }
        RecurringTask recurring = (RecurringTask) task;
        while (turns.size() < count) {
            due = recurring.getNextDueDate(due);
            assignee = CatchUpService.chooseAssignee(recurring, assignee, members);
            turns.add(new Turn(findMember(members, assignee, null), due));
        }
        return turns;
    }

    /**
     * Turns of repeating tasks that will come up in the Monday-to-Sunday week containing the given
     * day but don't exist yet. The app creates a turn only when the one before it is due
     * (CatchUpService), so without these a daily task would show just one day of the week.
     * They have id 0 and can't be ticked off or swapped until they're created.
     */
    public List<TaskAssignment> getPlannedTurns(LocalDate anyDayInWeek) throws SettlrException {
        if (anyDayInWeek == null) {
            throw new ValidationException("Please choose a day.");
        }
        int householdId = session.requireHouseholdId();
        LocalDateTime from = DateTimeUtil.weekStart(anyDayInWeek);
        LocalDateTime to = from.plusDays(7);
        LocalDateTime now = LocalDateTime.now();
        List<User> members = membershipDao.findActiveMembers(householdId);
        List<TaskAssignment> planned = new ArrayList<>();
        if (members.isEmpty()) {
            return planned;
        }
        for (Task task : taskDao.findActiveByHousehold(householdId)) {
            if (!(task instanceof RecurringTask)) {
                continue;
            }
            RecurringTask recurring = (RecurringTask) task;
            TaskAssignment latest = assignmentDao.findLatestForTask(task.getId());
            if (latest == null) {
                continue;
            }
            LocalDateTime due = latest.getDueAt();
            int assignee = latest.getAssigneeId();
            while (true) {
                due = recurring.getNextDueDate(due);
                if (!due.isBefore(to)) {
                    break;
                }
                assignee = CatchUpService.chooseAssignee(recurring, assignee, members);
                if (due.isBefore(from) || due.isBefore(now)) {
                    continue;
                }
                TaskAssignment turn = new TaskAssignment(task.getId(), assignee, due);
                turn.setTaskTitle(task.getTitle());
                turn.setTaskPoints(task.getPoints());
                turn.setAssigneeName(findMember(members, assignee, null).getDisplayName());
                planned.add(turn);
            }
        }
        planned.sort((a, b) -> a.getDueAt().compareTo(b.getDueAt()));
        return planned;
    }

    /** True when the current user may edit or archive this task (its creator or an Admin). */
    public boolean canModify(Task task) throws SettlrException {
        return task != null && (task.getCreatedBy() == session.requireUser().getId() || Session.getInstance().isAdmin());
    }

    /** True when the current user may tick this assignment off (its assignee or an Admin). */
    public boolean canComplete(TaskAssignment a) throws SettlrException {
        return a.getId() > 0 && a.getStatus() == TaskStatus.PENDING
                && (a.getAssigneeId() == session.requireUser().getId() || Session.getInstance().isAdmin());
    }

    private static User findMember(List<User> members, int userId, String fallbackName) {
        for (User u : members) {
            if (u.getId() == userId) {
                return u;
            }
        }
        // someone who left the household: a stand-in with just the name
        return new User(userId == 0 ? 1 : userId, "former_member", fallbackName == null ? "Former member" : fallbackName,
                "0000000000000000000000000000000000000000000000000000000000000000", LocalDateTime.now());
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
        if (task == null || task.isArchived() || task.getHouseholdId() != session.requireHouseholdId()) {
            throw new NotFoundException("That task could not be found.");
        }
        return task;
    }

    /** Only the creator or an Admin may change or delete a task (3.2). */
    private void requireCanModify(Task task) throws SettlrException {
        boolean creator = task.getCreatedBy() == session.requireUser().getId();
        if (!creator && !Session.getInstance().isAdmin()) {
            throw new PermissionDeniedException("Only the person who created this task or an Admin can change it.");
        }
    }

    /** Stops someone from touching another household's assignment by id. */
    private void requireInHousehold(TaskAssignment a) throws SettlrException {
        Task task = (a == null) ? null : taskDao.findById(a.getTaskId());
        if (task == null || task.getHouseholdId() != session.requireHouseholdId()) {
            throw new NotFoundException("That task could not be found.");
        }
    }

    // ---- session -------------------------------------------------------

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

    /** Credits the assignee even when an Admin clicked. */
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

}
