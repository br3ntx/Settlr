package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Membership;
import com.settlr.model.Role;
import com.settlr.model.User;

import java.util.List;

/**
 * The household_members table: who belongs to which household, with which role.
 * A membership has no single id (it's the pair household + user), so this isn't a Dao<T>.
 * Removed members are never deleted, only marked inactive, so their history stays.
 */
public interface MembershipDao {

    /** Everyone currently in the household, in the order they joined (rotation relies on it). */
    List<User> findActiveMembers(int householdId) throws DataAccessException;

    /** The household this user is in now, or null if they haven't joined one yet. */
    Membership findActiveMembershipOf(int userId) throws DataAccessException;

    /** One user's membership in one household (active or not), or null. */
    Membership find(int householdId, int userId) throws DataAccessException;

    /** Adds a user to a household, or re-activates them if they were removed before. */
    void insert(Membership m) throws DataAccessException;

    /** Everyone currently in the household with their role and join date, in join order. */
    List<Membership> findActiveMemberships(int householdId) throws DataAccessException;

    /** Changes a member's role (e.g. making someone else Admin). */
    void updateRole(int householdId, int userId, Role role) throws DataAccessException;

    /** Removes a member by marking them inactive (their history is kept). */
    void deactivate(int householdId, int userId) throws DataAccessException;
}
