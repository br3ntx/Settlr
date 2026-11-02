package com.settlr.service;

import com.settlr.dao.HouseholdDao;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.RecurringBillDao;
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
import com.settlr.model.Household;
import com.settlr.model.Membership;
import com.settlr.model.RecurringBill;
import com.settlr.model.RecurringTask;
import com.settlr.model.Role;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;
import com.settlr.util.InviteCodeGenerator;
import com.settlr.util.Session;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business rules for households: creating one, joining with an invite code,
 * listing and removing members, and the Admin-only settings.
 * Reads the current user and household from Session; all SQL stays in the DAOs.
 */
public class HouseholdService {

    private static final int MAX_CODE_ATTEMPTS = 10;

    /** One member as the Household page shows them. Read-only. */
    public static final class Member {
        private final User user;
        private final Role role;
        private final LocalDateTime joinedAt;

        Member(User user, Role role, LocalDateTime joinedAt) {
            this.user = user;
            this.role = role;
            this.joinedAt = joinedAt;
        }

        public User getUser() { return user; }
        public Role getRole() { return role; }
        public LocalDateTime getJoinedAt() { return joinedAt; }
        public boolean isAdmin() { return role == Role.ADMIN; }
    }

    private final HouseholdDao householdDao = new HouseholdDao();
    private final TaskDao taskDao = new TaskDao();
    private final TaskAssignmentDao assignmentDao = new TaskAssignmentDao();
    private final SwapRequestDao swapDao = new SwapRequestDao();
    private final RecurringBillDao billDao = new RecurringBillDao();
    private final MembershipDao membershipDao = new MembershipDao();
    private final ActivityService activityService = new ActivityService();

    /** Creates a household with the current user as Admin and stores it in the Session. */
    public Household create(String name) throws SettlrException {
        User user = requireLogin();

        if (name == null || name.isBlank()) {
            throw new ValidationException("Please enter a household name.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > Household.MAX_NAME_LENGTH) {
            throw new ValidationException("Household name must be at most "
                    + Household.MAX_NAME_LENGTH + " characters.");
        }
        requireNoHousehold(user.getId());

        Household household = new Household(0, trimmed, generateUniqueCode(), user.getId(), false);
        household.setId(householdDao.insert(household));

        membershipDao.insert(new Membership(household.getId(), user.getId(), Role.ADMIN,
                LocalDateTime.now(), true));

        Session.getInstance().setHousehold(household, Role.ADMIN);
        logMemberJoined(user, user.getDisplayName() + " created " + household.getName());
        return household;
    }

    /** Joins the household that owns this invite code, as a Member. */
    public Household join(String inviteCode) throws SettlrException {
        User user = requireLogin();

        if (inviteCode == null || inviteCode.isBlank()) {
            throw new ValidationException("Please enter an invite code.");
        }
        String code = inviteCode.trim().toUpperCase();
        if (!Household.isValidInviteCode(code)) {
            throw new ValidationException("Invite codes are " + Household.INVITE_CODE_LENGTH
                    + " characters long.");
        }
        requireNoHousehold(user.getId());

        Household household = householdDao.findByInviteCode(code);
        if (household == null) {
            throw new NotFoundException("That invite code doesn't match any household.");
        }

        membershipDao.insert(new Membership(household.getId(), user.getId(), Role.MEMBER,
                LocalDateTime.now(), true));

        Session.getInstance().setHousehold(household, Role.MEMBER);
        logMemberJoined(user, user.getDisplayName() + " joined the household");
        return household;
    }

    /** Active members of the current household, in join order. */
    public List<User> getMembers() throws SettlrException {
        requireLogin();
        return membershipDao.findActiveMembers(currentHousehold().getId());
    }

    /** Active members with their role and join date, in join order. */
    public List<Member> getMemberList() throws SettlrException {
        requireLogin();
        int householdId = currentHousehold().getId();
        Map<Integer, User> users = new HashMap<>();
        for (User u : membershipDao.findActiveMembers(householdId)) {
            users.put(u.getId(), u);
        }
        List<Member> list = new ArrayList<>();
        for (Membership m : membershipDao.findActiveMemberships(householdId)) {
            User u = users.get(m.getUserId());
            if (u != null) {
                list.add(new Member(u, m.getRole(), m.getJoinedAt()));
            }
        }
        return list;
    }

    /** Admin only. Renames the household. */
    public void rename(String name) throws SettlrException {
        requireLogin();
        requireAdmin();
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Please enter a household name.");
        }
        if (trimmed.length() > Household.MAX_NAME_LENGTH) {
            throw new ValidationException("Household name must be at most " + Household.MAX_NAME_LENGTH
                    + " characters.");
        }
        Household household = currentHousehold();
        String old = household.getName();
        household.setName(trimmed);
        try {
            householdDao.update(household);
        } catch (DataAccessException e) {
            household.setName(old); // keep the Session copy in sync with the database
            throw e;
        }
    }

    /**
     * Admin only. Makes someone an Admin or a Member. A household always keeps at least one
     * Admin, so the last one can't step down until someone else is Admin.
     */
    public void setRole(int userId, Role role) throws SettlrException {
        User me = requireLogin();
        requireAdmin();
        Household household = currentHousehold();
        Membership target = membershipDao.find(household.getId(), userId);
        if (target == null || !target.isActive()) {
            throw new NotFoundException("That person isn't an active member of this household.");
        }
        if (target.getRole() == role) {
            return;
        }
        if (role == Role.MEMBER && countAdmins() <= 1) {
            throw new ValidationException("A household needs at least one Admin. Make someone else Admin first.");
        }
        membershipDao.updateRole(household.getId(), userId, role);
        if (userId == me.getId()) {
            Session.getInstance().setHousehold(household, role);
        }
    }

    /**
     * Admin only. Marks a member inactive (history and rankings are kept), gives their
     * upcoming tasks to someone else, takes them off the monthly bills and makes a new
     * invite code so they can't rejoin with the old one. Returns the new code.
     * Money they owe stays on Expenses; the screen warns the Admin about it first.
     */
    public String removeMember(int userId) throws SettlrException {
        User me = requireLogin();
        requireAdmin();

        if (userId == me.getId()) {
            throw new ValidationException("Admins can't remove themselves.");
        }
        int householdId = currentHousehold().getId();
        Membership target = membershipDao.findActiveMembershipOf(userId);
        if (target == null || target.getHouseholdId() != householdId) {
            throw new NotFoundException("That person isn't an active member of this household.");
        }
        List<User> before = membershipDao.findActiveMembers(householdId);
        membershipDao.deactivate(householdId, userId);
        handOff(householdId, userId, before, me.getId());
        return regenerateInviteCode();
    }

    /**
     * Leaves the household. Not allowed while you still owe someone money, or while you're
     * the only Admin and others are still in it. Your upcoming tasks go to someone else.
     */
    public void leave() throws SettlrException {
        User me = requireLogin();
        Household household = currentHousehold();
        List<Member> members = getMemberList();
        if (Session.getInstance().isAdmin() && countAdmins() <= 1 && members.size() > 1) {
            throw new ValidationException("You're the only Admin. Make someone else Admin before you leave.");
        }
        if (!new SettlementService().getDebtsOf(me.getId()).isEmpty()) {
            throw new ValidationException("Settle up what you owe before you leave.");
        }
        int fallback = 0; // who gets your non-rotating tasks: another Admin, else anyone
        for (Member m : members) {
            if (m.getUser().getId() == me.getId()) {
                continue;
            }
            if (m.isAdmin()) {
                fallback = m.getUser().getId();
                break;
            }
            if (fallback == 0) {
                fallback = m.getUser().getId();
            }
        }
        List<User> before = membershipDao.findActiveMembers(household.getId());
        membershipDao.deactivate(household.getId(), me.getId());
        if (fallback != 0) {
            handOff(household.getId(), me.getId(), before, fallback);
        }
        Session.getInstance().setHousehold(null, null);
    }

    /** The household's monthly bills, running and paused. */
    public List<RecurringBill> getBills() throws SettlrException {
        requireLogin();
        return billDao.findByHousehold(currentHousehold().getId());
    }

    /** Admin only. A paused bill stops adding an expense each month until it's resumed. */
    public void setBillPaused(int billId, boolean paused) throws SettlrException {
        requireLogin();
        requireAdmin();
        RecurringBill bill = loadOwnBill(billId);
        if (!paused && bill.getMemberIds().isEmpty()) {
            throw new ValidationException("Nobody is on this bill anymore, so it can't be resumed.");
        }
        bill.setActive(!paused);
        billDao.update(bill);
    }

    /**
     * Admin only. Deletes a bill that hasn't added any expenses yet. One that has can only be
     * paused, because its past expenses still point to it.
     */
    public void deleteBill(int billId) throws SettlrException {
        requireLogin();
        requireAdmin();
        RecurringBill bill = loadOwnBill(billId);
        if (bill.getLastGenerated() != null) {
            throw new ValidationException("This bill already added expenses, so it can only be paused.");
        }
        billDao.delete(billId);
    }

    /** Admin only. Replaces the invite code and returns the new one. */
    public String regenerateInviteCode() throws SettlrException {
        requireLogin();
        requireAdmin();

        Household household = currentHousehold();
        String oldCode = household.getInviteCode();
        String newCode = generateUniqueCode();
        household.setInviteCode(newCode);
        try {
            householdDao.update(household);
        } catch (DataAccessException e) {
            household.setInviteCode(oldCode); // keep the Session copy in sync with the database
            throw e;
        }
        return newCode;
    }

    /** Admin only. Shows or hides the five "shame" rankings. */
    public void setHideShameRanks(boolean hide) throws SettlrException {
        requireLogin();
        requireAdmin();

        Household household = currentHousehold();
        boolean old = household.isHideShameRanks();
        household.setHideShameRanks(hide);
        try {
            householdDao.update(household);
        } catch (DataAccessException e) {
            household.setHideShameRanks(old);
            throw e;
        }
    }

    // ---------- helpers ----------

    /**
     * After someone leaves or is removed: their upcoming tasks go to the next person in the
     * rotation (or to fallbackId for tasks that don't rotate), swap requests to or from them
     * are declined, and they come off the monthly bills. A bill they paid, or one nobody is
     * left on, is paused. Past-due tasks stay theirs, so nobody else gets a late mark.
     */
    private void handOff(int householdId, int leavingId, List<User> before, int fallbackId) throws SettlrException {
        if (before.size() <= 1) {
            return; // nobody to hand anything to
        }
        LocalDateTime now = LocalDateTime.now();
        for (TaskAssignment a : assignmentDao.findBetween(householdId, now, now.plusYears(5))) {
            if (a.getAssigneeId() != leavingId || a.getStatus() != TaskStatus.PENDING) {
                continue;
            }
            Task task = taskDao.findById(a.getTaskId());
            boolean rotates = task instanceof RecurringTask && ((RecurringTask) task).isRotates();
            a.setAssigneeId(rotates ? nextAfter(before, leavingId) : fallbackId);
            assignmentDao.update(a);
        }
        List<SwapRequest> swaps = new ArrayList<>(swapDao.findIncoming(leavingId));
        swaps.addAll(swapDao.findOutgoing(leavingId));
        for (SwapRequest swap : swaps) {
            if (swap.getStatus() == SwapStatus.PENDING) {
                swap.setStatus(SwapStatus.DECLINED);
                swap.setRespondedAt(now);
                swapDao.update(swap);
            }
        }
        billDao.removeMemberFromBills(householdId, leavingId);
        for (RecurringBill bill : billDao.findByHousehold(householdId)) {
            if (bill.isActive() && (bill.getPaidBy() == leavingId || bill.getMemberIds().isEmpty())) {
                bill.setActive(false);
                billDao.update(bill);
            }
        }
    }

    /** The member after this one in join order (wrapping around), skipping them. */
    private static int nextAfter(List<User> members, int userId) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getId() == userId) {
                return members.get((i + 1) % members.size()).getId();
            }
        }
        return members.get(0).getId();
    }

    private int countAdmins() throws SettlrException {
        int admins = 0;
        for (Membership m : membershipDao.findActiveMemberships(currentHousehold().getId())) {
            if (m.getRole() == Role.ADMIN) {
                admins++;
            }
        }
        return admins;
    }

    private RecurringBill loadOwnBill(int billId) throws SettlrException {
        RecurringBill bill = billDao.findById(billId);
        if (bill == null || bill.getHouseholdId() != currentHousehold().getId()) {
            throw new NotFoundException("That bill could not be found.");
        }
        return bill;
    }

    private User requireLogin() throws AuthException {
        Session session = Session.getInstance();
        if (!session.isLoggedIn()) {
            throw new AuthException("Please log in first.");
        }
        return session.getCurrentUser();
    }

    private void requireAdmin() throws PermissionDeniedException {
        if (!Session.getInstance().isAdmin()) {
            throw new PermissionDeniedException("Only the household Admin can do that.");
        }
    }

    private Household currentHousehold() throws NotFoundException {
        Household household = Session.getInstance().getCurrentHousehold();
        if (household == null) {
            throw new NotFoundException("You aren't in a household yet.");
        }
        return household;
    }

    /**
     * Adds a "joined" entry to the activity feed (master doc 3.8).
     * The user is already in the household at this point, so if only the
     * feed entry fails we print it instead of reporting the join as failed.
     */
    private void logMemberJoined(User user, String message) {
        try {
            activityService.log(ActivityType.MEMBER_JOINED, user.getId(), message);
        } catch (SettlrException e) {
            System.err.println("Couldn't add the activity feed entry: " + e.getMessage());
        }
    }

    /** A user belongs to one household at a time. */
    private void requireNoHousehold(int userId) throws DataAccessException, ValidationException {
        if (membershipDao.findActiveMembershipOf(userId) != null) {
            throw new ValidationException("You're already in a household.");
        }
    }

    /** Invite codes must be unique across households, so retry if one is taken. */
    private String generateUniqueCode() throws DataAccessException, ValidationException {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = InviteCodeGenerator.generate();
            if (householdDao.findByInviteCode(code) == null) {
                return code;
            }
        }
        throw new ValidationException("Couldn't make an invite code. Please try again.");
    }
}
