package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.User;
import com.settlr.service.HouseholdService;
import com.settlr.service.SwapService;
import com.settlr.service.TaskService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One week of the Tasks page: the household's members and tasks, the week's turns, what's overdue,
 * the swaps you asked for and the swaps waiting for you. Loaded in one go on a background thread
 * (see TasksScreen.reload), then only read by the screen and its rows.
 */
final class TaskWeek {

    final Map<Integer, User> members = new LinkedHashMap<>();           // join order
    final Map<Integer, Task> tasks = new HashMap<>();
    final Map<Integer, SwapRequest> myPendingSwaps = new HashMap<>();   // by assignment id
    final List<SwapRequest> waitingForMe = new ArrayList<>();
    final List<TaskAssignment> week = new ArrayList<>();
    final List<TaskAssignment> overdue = new ArrayList<>();

    /** Nothing loaded: what the page shows when loading failed. */
    static TaskWeek empty() {
        return new TaskWeek();
    }

    /**
     * Asks the services for the week starting on weekStart. Overdue turns are only loaded for the
     * current week. Database work: call it from a background load.
     */
    static TaskWeek load(TaskService taskService, SwapService swapService, HouseholdService householdService,
                         LocalDate weekStart, boolean currentWeek) throws SettlrException {
        TaskWeek w = new TaskWeek();
        for (User u : householdService.getMembers()) {
            w.members.put(u.getId(), u);
        }
        for (Task t : taskService.getActiveTasks()) {
            w.tasks.put(t.getId(), t);
        }
        w.week.addAll(taskService.getWeek(weekStart));
        w.week.addAll(taskService.getPlannedTurns(weekStart)); // turns of repeating tasks not created yet
        w.week.sort(Comparator.comparing(TaskAssignment::getDueAt));
        if (currentWeek) {
            w.overdue.addAll(taskService.getOverdue());
        }
        for (SwapRequest s : swapService.getOutgoing()) {
            if (s.getStatus() == SwapStatus.PENDING) {
                w.myPendingSwaps.put(s.getAssignmentId(), s);
            }
        }
        for (SwapRequest s : swapService.getIncoming()) {
            if (s.getStatus() == SwapStatus.PENDING && s.getAssignmentDueAt().isAfter(LocalDateTime.now())) {
                w.waitingForMe.add(s);
            }
        }
        return w;
    }
}
