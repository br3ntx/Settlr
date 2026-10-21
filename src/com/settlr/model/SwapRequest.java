package com.settlr.model;

import java.time.LocalDateTime;

/**
 * A request from one member to hand an assignment to another member.
 * Mirrors the swap_requests table. The four "display" fields are filled by
 * SwapRequestDao with a JOIN for SwapRequestsDialog; they are not columns.
 */
public class SwapRequest {

    private int id;                         // 0 = not saved yet
    private int assignmentId;
    private int fromUser;
    private int toUser;
    private SwapStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;      // null while PENDING

    // Read-only display data (filled by JOIN, never written back)
    private String taskTitle;
    private LocalDateTime assignmentDueAt;
    private String fromUserName;
    private String toUserName;

    public SwapRequest(int assignmentId, int fromUser, int toUser) {
        setAssignmentId(assignmentId);
        setFromUser(fromUser);
        setToUser(toUser);
        if (fromUser == toUser) {
            throw new IllegalArgumentException("You cannot swap a task with yourself.");
        }
        this.status = SwapStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }

    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Swap id cannot be negative.");
        }
        this.id = id;
    }

    public int getAssignmentId() { return assignmentId; }

    public void setAssignmentId(int assignmentId) {
        if (assignmentId <= 0) {
            throw new IllegalArgumentException("A swap must refer to an assignment.");
        }
        this.assignmentId = assignmentId;
    }

    public int getFromUser() { return fromUser; }

    public void setFromUser(int fromUser) {
        if (fromUser <= 0) {
            throw new IllegalArgumentException("A swap needs a sender.");
        }
        this.fromUser = fromUser;
    }

    public int getToUser() { return toUser; }

    public void setToUser(int toUser) {
        if (toUser <= 0) {
            throw new IllegalArgumentException("Please choose who to ask.");
        }
        this.toUser = toUser;
    }

    public SwapStatus getStatus() { return status; }

    public void setStatus(SwapStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status is required.");
        }
        this.status = status;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) {
        if (createdAt == null) {
            throw new IllegalArgumentException("Created time is required.");
        }
        this.createdAt = createdAt;
    }

    public LocalDateTime getRespondedAt() { return respondedAt; }

    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public String getTaskTitle() { return taskTitle; }

    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }

    public LocalDateTime getAssignmentDueAt() { return assignmentDueAt; }

    public void setAssignmentDueAt(LocalDateTime assignmentDueAt) { this.assignmentDueAt = assignmentDueAt; }

    public String getFromUserName() { return fromUserName; }

    public void setFromUserName(String fromUserName) { this.fromUserName = fromUserName; }

    public String getToUserName() { return toUserName; }

    public void setToUserName(String toUserName) { this.toUserName = toUserName; }
}
