package com.settlr.dao.memory;

import com.settlr.dao.TaskAssignmentDao;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Task turns kept in a list. Like the database, every find hands back a fresh copy with the
 * task title, points and assignee name filled in, so a service only changes what it saves.
 */
class InMemoryTaskAssignmentDao implements TaskAssignmentDao {

    private final InMemoryDatabase db;

    InMemoryTaskAssignmentDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public TaskAssignment findById(int id) {
        TaskAssignment a = db.assignment(id);
        return a == null ? null : copy(a);
    }

    @Override
    public List<TaskAssignment> findBetween(int householdId, LocalDateTime from, LocalDateTime to) {
        List<TaskAssignment> result = new ArrayList<>();
        for (TaskAssignment a : db.assignments) {
            Task t = db.task(a.getTaskId());
            if (t != null && t.getHouseholdId() == householdId
                    && !a.getDueAt().isBefore(from) && a.getDueAt().isBefore(to)) {
                result.add(copy(a));
            }
        }
        result.sort(Comparator.comparing(TaskAssignment::getDueAt));
        return result;
    }

    @Override
    public List<TaskAssignment> findPendingDueBefore(LocalDateTime cutoff) {
        List<TaskAssignment> result = new ArrayList<>();
        for (TaskAssignment a : db.assignments) {
            if (a.getStatus() == TaskStatus.PENDING && a.getDueAt().isBefore(cutoff)) {
                result.add(copy(a));
            }
        }
        result.sort(Comparator.comparing(TaskAssignment::getDueAt));
        return result;
    }

    @Override
    public TaskAssignment findLatestForTask(int taskId) {
        TaskAssignment latest = null;
        for (TaskAssignment a : db.assignments) {
            if (a.getTaskId() == taskId && (latest == null || a.getDueAt().isAfter(latest.getDueAt()))) {
                latest = a;
            }
        }
        return latest == null ? null : copy(latest);
    }

    @Override
    public int insert(TaskAssignment a) {
        TaskAssignment saved = copy(a);
        saved.setId(db.newId());
        db.assignments.add(saved);
        a.setId(saved.getId());
        return saved.getId();
    }

    @Override
    public int insert(Connection c, TaskAssignment a) {
        return insert(a);
    }

    /** Same rule as UNIQUE (task_id, due_at) in MySQL. */
    @Override
    public boolean insertIfAbsent(TaskAssignment a) {
        for (TaskAssignment existing : db.assignments) {
            if (existing.getTaskId() == a.getTaskId() && existing.getDueAt().equals(a.getDueAt())) {
                return false;
            }
        }
        insert(a);
        return true;
    }

    @Override
    public boolean insertIfAbsent(Connection c, TaskAssignment a) {
        return insertIfAbsent(a);
    }

    @Override
    public void update(TaskAssignment a) {
        TaskAssignment saved = db.assignment(a.getId());
        if (saved != null) {
            saved.setAssigneeId(a.getAssigneeId());
            saved.setCompletedAt(a.getCompletedAt());
            saved.setStatus(a.getStatus());
        }
    }

    @Override
    public void update(Connection c, TaskAssignment a) {
        update(a);
    }

    @Override
    public void delete(int id) {
        db.assignments.removeIf(a -> a.getId() == id);
    }

    @Override
    public void deletePendingAfter(Connection c, int taskId, LocalDateTime after) {
        db.assignments.removeIf(a -> a.getTaskId() == taskId && a.getStatus() == TaskStatus.PENDING
                && a.getDueAt().isAfter(after));
    }

    /** What the SELECT with its JOINs would give back. */
    private TaskAssignment copy(TaskAssignment a) {
        TaskAssignment c = new TaskAssignment(a.getTaskId(), a.getAssigneeId(), a.getDueAt());
        c.setId(a.getId());
        c.setCompletedAt(a.getCompletedAt());
        c.setStatus(a.getStatus());
        Task t = db.task(a.getTaskId());
        if (t != null) {
            c.setTaskTitle(t.getTitle());
            c.setTaskPoints(t.getPoints());
        }
        User u = db.user(a.getAssigneeId());
        c.setAssigneeName(u == null ? null : u.getDisplayName());
        return c;
    }
}
