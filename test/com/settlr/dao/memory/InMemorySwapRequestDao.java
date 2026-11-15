package com.settlr.dao.memory;

import com.settlr.dao.SwapRequestDao;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Swap requests kept in a list. Finds hand back copies with the task and names filled in. */
class InMemorySwapRequestDao implements SwapRequestDao {

    private final InMemoryDatabase db;

    InMemorySwapRequestDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public SwapRequest findById(int id) {
        for (SwapRequest s : db.swaps) {
            if (s.getId() == id) {
                return copy(s);
            }
        }
        return null;
    }

    @Override
    public List<SwapRequest> findIncoming(int userId) {
        List<SwapRequest> result = new ArrayList<>();
        for (SwapRequest s : db.swaps) {
            if (s.getToUser() == userId) {
                result.add(0, copy(s)); // newest first
            }
        }
        return result;
    }

    @Override
    public List<SwapRequest> findOutgoing(int userId) {
        List<SwapRequest> result = new ArrayList<>();
        for (SwapRequest s : db.swaps) {
            if (s.getFromUser() == userId) {
                result.add(0, copy(s));
            }
        }
        return result;
    }

    @Override
    public SwapRequest findPendingForAssignment(int assignmentId) {
        for (SwapRequest s : db.swaps) {
            if (s.getAssignmentId() == assignmentId && s.getStatus() == SwapStatus.PENDING) {
                return copy(s);
            }
        }
        return null;
    }

    @Override
    public List<SwapRequest> findPendingPastDue(LocalDateTime now) {
        List<SwapRequest> result = new ArrayList<>();
        for (SwapRequest s : db.swaps) {
            TaskAssignment a = db.assignment(s.getAssignmentId());
            if (s.getStatus() == SwapStatus.PENDING && a != null && a.getDueAt().isBefore(now)) {
                result.add(copy(s));
            }
        }
        return result;
    }

    @Override
    public int insert(SwapRequest s) {
        s.setId(db.newId());
        db.swaps.add(copy(s));
        return s.getId();
    }

    @Override
    public void update(SwapRequest s) {
        for (SwapRequest saved : db.swaps) {
            if (saved.getId() == s.getId()) {
                saved.setStatus(s.getStatus());
                saved.setRespondedAt(s.getRespondedAt());
            }
        }
    }

    @Override
    public void update(Connection c, SwapRequest s) {
        update(s);
    }

    @Override
    public void delete(int id) {
        db.swaps.removeIf(s -> s.getId() == id);
    }

    @Override
    public void deleteByTaskAfter(Connection c, int taskId, LocalDateTime after) {
        db.swaps.removeIf(s -> {
            TaskAssignment a = db.assignment(s.getAssignmentId());
            return a != null && a.getTaskId() == taskId && a.getStatus() == TaskStatus.PENDING
                    && a.getDueAt().isAfter(after);
        });
    }

    /** What the SELECT with its JOINs would give back: a copy with the task and names filled in. */
    private SwapRequest copy(SwapRequest s) {
        SwapRequest c = new SwapRequest(s.getAssignmentId(), s.getFromUser(), s.getToUser());
        c.setId(s.getId());
        c.setStatus(s.getStatus());
        c.setCreatedAt(s.getCreatedAt());
        c.setRespondedAt(s.getRespondedAt());
        TaskAssignment a = db.assignment(s.getAssignmentId());
        if (a != null) {
            c.setAssignmentDueAt(a.getDueAt());
            Task t = db.task(a.getTaskId());
            c.setTaskTitle(t == null ? null : t.getTitle());
        }
        User from = db.user(s.getFromUser());
        User to = db.user(s.getToUser());
        c.setFromUserName(from == null ? null : from.getDisplayName());
        c.setToUserName(to == null ? null : to.getDisplayName());
        return c;
    }
}
