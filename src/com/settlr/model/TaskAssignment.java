package com.settlr.model;

import java.time.LocalDateTime;

/**
 * One actual due date of a task, with one assignee. The rankings read these
 * rows, so the DONE / DONE_LATE decision lives here in one place.
 *
 * The three "display" fields are not columns of task_assignments. TaskAssignmentDao
 * fills them with a JOIN so screens can show titles and names without extra queries.
 */
public class TaskAssignment {

    private int id;                     // 0 = not saved yet
    private int taskId;
    private int assigneeId;
    private LocalDateTime dueAt;
    private LocalDateTime completedAt;  // null until completed
    private TaskStatus status;

    // Read-only display data (filled by JOIN, never written back)
    private String taskTitle;
    private int taskPoints;
    private String assigneeName;

    public TaskAssignment(int taskId, int assigneeId, LocalDateTime dueAt) {
        setTaskId(taskId);
        setAssigneeId(assigneeId);
        setDueAt(dueAt);
        this.status = TaskStatus.PENDING;
    }

    /**
     * Completes the assignment. On time (completedAt <= dueAt) is DONE,
     * otherwise DONE_LATE. Only a PENDING assignment can be completed, which
     * also blocks MISSED ones.
     */
    public void markComplete(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("Completion time is required.");
        }
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException("This task can no longer be completed.");
        }
        this.completedAt = now;
        this.status = now.isAfter(dueAt) ? TaskStatus.DONE_LATE : TaskStatus.DONE;
    }

    /** True when still PENDING and the due time has passed. */
    public boolean isOverdue(LocalDateTime now) {
        return status == TaskStatus.PENDING && now.isAfter(dueAt);
    }

    public int getId() { return id; }

    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Assignment id cannot be negative.");
        }
        this.id = id;
    }

    public int getTaskId() { return taskId; }

    public void setTaskId(int taskId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("An assignment must belong to a task.");
        }
        this.taskId = taskId;
    }

    public int getAssigneeId() { return assigneeId; }

    public void setAssigneeId(int assigneeId) {
        if (assigneeId <= 0) {
            throw new IllegalArgumentException("An assignment needs an assignee.");
        }
        this.assigneeId = assigneeId;
    }

    public LocalDateTime getDueAt() { return dueAt; }

    public void setDueAt(LocalDateTime dueAt) {
        if (dueAt == null) {
            throw new IllegalArgumentException("An assignment needs a due date.");
        }
        this.dueAt = dueAt;
    }

    public LocalDateTime getCompletedAt() { return completedAt; }

    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public TaskStatus getStatus() { return status; }

    public void setStatus(TaskStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status is required.");
        }
        this.status = status;
    }

    public String getTaskTitle() { return taskTitle; }

    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }

    public int getTaskPoints() { return taskPoints; }

    public void setTaskPoints(int taskPoints) { this.taskPoints = taskPoints; }

    public String getAssigneeName() { return assigneeName; }

    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
}
