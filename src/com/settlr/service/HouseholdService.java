package com.settlr.service;

import com.settlr.dao.HouseholdDao;
import com.settlr.dao.MembershipDao;
import com.settlr.exception.AuthException;
import com.settlr.exception.DataAccessException;
import com.settlr.exception.NotFoundException;
import com.settlr.exception.PermissionDeniedException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.Household;
import com.settlr.model.Membership;
import com.settlr.model.Role;
import com.settlr.model.User;
import com.settlr.util.InviteCodeGenerator;
import com.settlr.util.Session;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Business rules for households: creating one, joining with an invite code,
 * listing and removing members, and the Admin-only settings.
 * Reads the current user and household from Session; all SQL stays in the DAOs.
 */
public class HouseholdService {

    private static final int MAX_CODE_ATTEMPTS = 10;

    private final HouseholdDao householdDao = new HouseholdDao();
    private final MembershipDao membershipDao = new MembershipDao();

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
        return household;
    }

    /** Active members of the current household, in join order. */
    public List<User> getMembers() throws SettlrException {
        requireLogin();
        return membershipDao.findActiveMembers(currentHousehold().getId());
    }

    /** Admin only. Marks a member inactive (history and rankings are kept). */
    public void removeMember(int userId) throws SettlrException {
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
        membershipDao.deactivate(householdId, userId);
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
