package com.settlr.service;

import com.settlr.dao.ActivityLogDao;
import com.settlr.dao.MembershipDao;
import com.settlr.exception.AuthException;
import com.settlr.exception.NotFoundException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.ActivityLog;
import com.settlr.model.ActivityType;
import com.settlr.model.Household;
import com.settlr.model.Membership;
import com.settlr.util.Session;

import java.util.List;

/**
 * Writes and reads the activity feed. Other services (tasks, expenses, payments, swaps,
 * households) call log() whenever something worth showing happens.
 *
 * <p>Why log() finds the household from the user's membership and not from Session: some events
 * are logged at startup catch-up (missed tasks), before anyone has logged in, so Session is empty.
 */
public class ActivityService {

    private final ActivityLogDao activityLogDao = new ActivityLogDao();
    private final MembershipDao membershipDao = new MembershipDao();

    /**
     * Records one event for the household the user is currently an active member of.
     * If that user has no active household (for example a removed member whose old task got missed),
     * there is no feed to show it in, so nothing is saved and nothing fails.
     * Over-long messages are shortened instead of rejected, because a failed log must never
     * block the real action (completing a task, adding an expense) that triggered it.
     */
    public void log(ActivityType type, int userId, String message) throws SettlrException {
        if (type == null) {
            throw new ValidationException("Please say what kind of activity this is.");
        }
        if (message == null || message.isBlank()) {
            throw new ValidationException("An activity needs a message.");
        }

        Membership membership = membershipDao.findActiveMembershipOf(userId);
        if (membership == null) {
            return;
        }

        String text = message.strip();
        if (text.length() > ActivityLog.MAX_MESSAGE_LENGTH) {
            text = text.substring(0, ActivityLog.MAX_MESSAGE_LENGTH - 1) + "\u2026";
        }
        activityLogDao.insert(new ActivityLog(membership.getHouseholdId(), userId, type, text));
    }

    /** The newest events of the logged-in user's household, newest first. */
    public List<ActivityLog> getFeed(int limit) throws SettlrException {
        if (limit < 1) {
            throw new ValidationException("The feed needs to show at least 1 event.");
        }
        Session session = Session.getInstance();
        if (!session.isLoggedIn()) {
            throw new AuthException("Please log in first.");
        }
        Household household = session.getCurrentHousehold();
        if (household == null) {
            throw new NotFoundException("Join or create a household to see its activity.");
        }
        return activityLogDao.findRecent(household.getId(), limit);
    }
}
