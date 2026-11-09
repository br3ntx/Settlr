package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.OwnedPet;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * The Shop and pets: owned_items (including each pet's name and outfit), users.active_pet,
 * xp_transactions, and the XP a user has earned. Rows here have no id of their own, so this
 * isn't a Dao<T>.
 */
public interface ShopDao {

    /** XP for each task point done on time / done late, and for each payment made on time. */
    int XP_PER_POINT = 10;
    int XP_PER_POINT_LATE = 5;
    int XP_PER_PAYMENT = 20;

    /** XP earned from tasks and confirmed on-time payments since the given time (e.g. this week). */
    int findEarnedSince(int userId, LocalDateTime since) throws DataAccessException;

    /** XP the user has right now: earned, minus what they spent, plus what they got back. */
    int findBalance(int userId) throws DataAccessException;

    int findBalance(Connection c, int userId) throws DataAccessException;

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
