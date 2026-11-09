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
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;
import com.settlr.util.Transaction;
import com.settlr.util.Session;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Business rules for swap requests (master doc 3.4): who may ask, who may
 * answer, and what accepting changes. Reads the current user and household
 * from Session and never contains SQL; the DAOs do that.
 */
public class SwapService {

    private final Session session = Session.getInstance();

    private static final String SAVE_FAILED = "Could not save your changes. Please try again.";

    private final SwapRequestDao swapDao = DaoFactory.get().swaps();
    private final TaskAssignmentDao assignmentDao = DaoFactory.get().assignments();
    private final TaskDao taskDao = DaoFactory.get().tasks();
    private final MembershipDao membershipDao = DaoFactory.get().memberships();
    private final ActivityService activityService = new ActivityService();

    // ---- public API (master doc 8.4) -----------------------------------

    /** The assignee of a PENDING assignment asks another active member to take it. */
    public void request(int assignmentId, int toUserId) throws SettlrException {
        int userId = session.requireUser().getId();
        TaskAssignment a = loadAssignmentOfHousehold(assignmentId);

        if (a.getAssigneeId() != userId) {
            throw new PermissionDeniedException("Only the person assigned to this task can ask for a swap.");
        }
        requireSwappable(a);
        if (toUserId == userId) {
            throw new ValidationException("You cannot swap a task with yourself.");
        }
        requireActiveMember(toUserId);
        if (swapDao.findPendingForAssignment(assignmentId) != null) {
            throw new ValidationException("This task already has a pending swap request.");
        }
        swapDao.insert(new SwapRequest(assignmentId, userId, toUserId));
    }

    /**
     * The receiver takes the task: the assignment's assignee changes and the
     * swap becomes ACCEPTED together, or not at all.
     */
    public void accept(int swapId) throws SettlrException {
        int userId = session.requireUser().getId();
        SwapRequest swap = loadPendingSwapForReceiver(swapId, userId);
        TaskAssignment a = loadAssignmentOfHousehold(swap.getAssignmentId());
        requireSwappable(a);

        LocalDateTime now = LocalDateTime.now();
        a.setAssigneeId(userId);
        swap.setStatus(SwapStatus.ACCEPTED);
        swap.setRespondedAt(now);
        Transaction.run(SAVE_FAILED, c -> {
            assignmentDao.update(c, a);
            swapDao.update(c, swap);
        });
        activityService.log(ActivityType.SWAP_ACCEPTED, userId,
                swap.getToUserName() + " took " + swap.getTaskTitle() + " from " + swap.getFromUserName());
    }

    /** The receiver says no: only the swap changes, the assignment stays. */
    public void decline(int swapId) throws SettlrException {
        int userId = session.requireUser().getId();
        SwapRequest swap = loadPendingSwapForReceiver(swapId, userId);

        swap.setStatus(SwapStatus.DECLINED);
        swap.setRespondedAt(LocalDateTime.now());
        swapDao.update(swap);
    }

    /** Every swap asked of the current user, newest first. */
    public List<SwapRequest> getIncoming() throws SettlrException {
        return swapDao.findIncoming(session.requireUser().getId());
    }

    /** Every swap the current user asked for, newest first. */
    public List<SwapRequest> getOutgoing() throws SettlrException {
        return swapDao.findOutgoing(session.requireUser().getId());
    }

    // ---- validation ----------------------------------------------------

    /** Only a PENDING assignment that is not yet past due can change hands. */
    private void requireSwappable(TaskAssignment a) throws SettlrException {
        if (a.getStatus() != TaskStatus.PENDING || a.isOverdue(LocalDateTime.now())) {
            throw new ValidationException("This task can no longer be swapped.");
        }
    }

    private SwapRequest loadPendingSwapForReceiver(int swapId, int userId) throws SettlrException {
        SwapRequest swap = swapDao.findById(swapId);
        if (swap == null) {
            throw new NotFoundException("That swap request could not be found.");
        }
        if (swap.getToUser() != userId) {
            throw new PermissionDeniedException("Only the person who was asked can answer this swap request.");
        }
        if (swap.getStatus() != SwapStatus.PENDING) {
            throw new ValidationException("This swap request was already answered.");
        }
        return swap;
    }

    /** Loads an assignment and checks it belongs to the current household. */
    private TaskAssignment loadAssignmentOfHousehold(int assignmentId) throws SettlrException {
        TaskAssignment a = assignmentDao.findById(assignmentId);
        Task task = (a == null) ? null : taskDao.findById(a.getTaskId());
        if (task == null || task.getHouseholdId() != session.requireHouseholdId()) {
            throw new NotFoundException("That task could not be found.");
        }
        return a;
    }

    private void requireActiveMember(int userId) throws SettlrException {
        for (User member : membershipDao.findActiveMembers(session.requireHouseholdId())) {
            if (member.getId() == userId) {
                return;
            }
        }
        throw new ValidationException("You can only ask an active member of your household.");
    }

    // ---- session and transaction helpers -------------------------------

}
