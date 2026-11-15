package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.OwnedPet;
import com.settlr.model.XpActivity;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * The Shop and pets: owned_items (including each pet's name and outfit), users.active_pet,
 * xp_transactions, and what a user did to earn XP. Rows here have no id of their own, so this
 * isn't a Dao<T>.
 */
public interface ShopDao {

    /**
     * What the user did that earns XP (task points on time / late, payments on time), counted
     * from the given time on, or over all time when since is null. The XP it is worth is
     * worked out by XpRules in the service layer.
     */
    XpActivity findXpActivity(int userId, LocalDateTime since) throws DataAccessException;

    /** The same counts over all time, read inside a transaction. */
    XpActivity findXpActivity(Connection c, int userId) throws DataAccessException;

    /** The total of xp_transactions: XP spent (negative) plus XP given back (positive). */
    int findXpAdjustments(Connection c, int userId) throws DataAccessException;

    /** The same total, read outside a transaction. */
    int findXpAdjustments(int userId) throws DataAccessException;

    /** The codes of everything the user owns (hats and pets), oldest first. */
    Set<String> findOwnedCodes(int userId) throws DataAccessException;

    Set<String> findOwnedCodes(Connection c, int userId) throws DataAccessException;

    /** Locks the user until the transaction ends, so two purchases can't spend the same XP. */
    void lockUser(Connection c, int userId) throws DataAccessException;

    void insertOwned(Connection c, int userId, String itemCode) throws DataAccessException;

    /** Records XP spent (negative amount) or given back (positive). */
    void insertTransaction(Connection c, int userId, int amount, String reason) throws DataAccessException;

    /** The user's pets with their names and outfits, oldest first. */
    List<OwnedPet> findPets(int userId) throws DataAccessException;

    /** The code of the pet the user shows on Home, or null. */
    String findActivePet(int userId) throws DataAccessException;

    /** Saves a pet's name and outfit. */
    void updatePet(Connection c, int userId, OwnedPet pet) throws DataAccessException;

    void updateActivePet(Connection c, int userId, String petCode) throws DataAccessException;
}
