package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.OwnedPet;
import com.settlr.model.XpActivity;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * The Shop and pets: owned_items (including each pet's name and outfit), households.active_pet,
 * xp_transactions, and what the household did to earn XP. Everything here belongs to a whole
 * household: its members share one pet, one set of items and one pool of XP. Rows here have
 * no id of their own, so this isn't a Dao<T>.
 */
public interface ShopDao {

    /**
     * What the household's members did that earns XP (task points on time / late, payments on
     * time), counted from the given time on, or over all time when since is null. The XP it is
     * worth is worked out by XpRules in the service layer.
     */
    XpActivity findXpActivity(int householdId, LocalDateTime since) throws DataAccessException;

    /** The same counts over all time, read inside a transaction. */
    XpActivity findXpActivity(Connection c, int householdId) throws DataAccessException;

    /** The total of the household's xp_transactions: XP spent (negative) plus XP given back (positive). */
    int findXpAdjustments(Connection c, int householdId) throws DataAccessException;

    /** The same total, read outside a transaction. */
    int findXpAdjustments(int householdId) throws DataAccessException;

    /** The codes of everything the household owns (hats and pets), oldest first. */
    Set<String> findOwnedCodes(int householdId) throws DataAccessException;

    Set<String> findOwnedCodes(Connection c, int householdId) throws DataAccessException;

    /**
     * Locks the household until the transaction ends, so two members buying at the same time
     * can't spend the same XP.
     */
    void lockHousehold(Connection c, int householdId) throws DataAccessException;

    void insertOwned(Connection c, int householdId, String itemCode) throws DataAccessException;

    /** Records XP the household spent (negative amount) or got back (positive), and who did it. */
    void insertTransaction(Connection c, int householdId, int userId, int amount, String reason)
            throws DataAccessException;

    /** The household's pets with their names and outfits, oldest first. */
    List<OwnedPet> findPets(int householdId) throws DataAccessException;

    /** The code of the pet the household shows on Home, or null. */
    String findActivePet(int householdId) throws DataAccessException;

    /** Saves a pet's name and outfit. */
    void updatePet(Connection c, int householdId, OwnedPet pet) throws DataAccessException;

    void updateActivePet(Connection c, int householdId, String petCode) throws DataAccessException;
}
