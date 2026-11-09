package com.settlr.dao.memory;

import com.settlr.dao.MembershipDao;
import com.settlr.model.Membership;
import com.settlr.model.Role;
import com.settlr.model.User;

import java.util.ArrayList;
import java.util.List;

/** Household memberships kept in a list, in the order they were added (= join order). */
class InMemoryMembershipDao implements MembershipDao {

    private final InMemoryDatabase db;

    InMemoryMembershipDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public List<User> findActiveMembers(int householdId) {
        List<User> members = new ArrayList<>();
        InMemoryUserDao users = new InMemoryUserDao(db);
        for (Membership m : findActiveMemberships(householdId)) {
            members.add(users.findById(m.getUserId()));
        }
        return members;
    }

    @Override
    public Membership findActiveMembershipOf(int userId) {
        for (Membership m : db.memberships) {
            if (m.getUserId() == userId && m.isActive()) {
                return m;
            }
        }
        return null;
    }

    @Override
    public Membership find(int householdId, int userId) {
        for (Membership m : db.memberships) {
            if (m.getHouseholdId() == householdId && m.getUserId() == userId) {
                return m;
            }
        }
        return null;
    }

    @Override
    public void insert(Membership m) {
        Membership old = find(m.getHouseholdId(), m.getUserId());
        if (old != null) {
            db.memberships.remove(old);
        }
        db.memberships.add(m);
    }

    @Override
    public List<Membership> findActiveMemberships(int householdId) {
        List<Membership> result = new ArrayList<>();
        for (Membership m : db.memberships) {
            if (m.getHouseholdId() == householdId && m.isActive()) {
                result.add(m);
            }
        }
        return result;
    }

    @Override
    public void updateRole(int householdId, int userId, Role role) {
        Membership m = find(householdId, userId);
        if (m != null) {
            m.setRole(role);
        }
    }

    @Override
    public void deactivate(int householdId, int userId) {
        Membership m = find(householdId, userId);
        if (m != null) {
            m.setActive(false);
        }
    }
}
