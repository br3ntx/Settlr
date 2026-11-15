package com.settlr.dao.memory;

import com.settlr.dao.ShopDao;
import com.settlr.model.OwnedPet;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.XpActivity;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Shop in memory. XP activity is counted from the in-memory task turns (payments aren't
 * counted here), so tests can check the XP rules end to end without MySQL.
 */
class InMemoryShopDao implements ShopDao {

    private final InMemoryDatabase db;

    InMemoryShopDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public XpActivity findXpActivity(int userId, LocalDateTime since) {
        int onTime = 0;
        int late = 0;
        for (TaskAssignment a : db.assignments) {
            if (a.getAssigneeId() != userId || a.getCompletedAt() == null
                    || (since != null && a.getCompletedAt().isBefore(since))) {
                continue;
            }
            int points = db.task(a.getTaskId()).getPoints();
            if (a.getStatus() == TaskStatus.DONE) {
                onTime += points;
            } else if (a.getStatus() == TaskStatus.DONE_LATE) {
                late += points;
            }
        }
        return new XpActivity(onTime, late, 0);
    }

    /** The same counts over all time (in memory there is no Connection to use). */
    @Override
    public XpActivity findXpActivity(Connection c, int userId) {
        return findXpActivity(userId, null);
    }

    @Override
    public int findXpAdjustments(Connection c, int userId) {
        return findXpAdjustments(userId);
    }

    @Override
    public int findXpAdjustments(int userId) {
        int total = 0;
        for (int[] tx : db.xpTransactions) {
            if (tx[0] == userId) {
                total += tx[1];
            }
        }
        return total;
    }

    @Override
    public Set<String> findOwnedCodes(int userId) {
        return new LinkedHashSet<>(db.owned.getOrDefault(userId, new ArrayList<>()));
    }

    @Override
    public Set<String> findOwnedCodes(Connection c, int userId) {
        return findOwnedCodes(userId);
    }

    @Override
    public void lockUser(Connection c, int userId) {
        // one test thread: nothing to lock
    }

    @Override
    public void insertOwned(Connection c, int userId, String itemCode) {
        db.owned.computeIfAbsent(userId, k -> new ArrayList<>()).add(itemCode);
    }

    @Override
    public void insertTransaction(Connection c, int userId, int amount, String reason) {
        db.xpTransactions.add(new int[] {userId, amount});
    }

    @Override
    public List<OwnedPet> findPets(int userId) {
        throw new UnsupportedOperationException("Pets aren't kept in memory yet.");
    }

    @Override
    public String findActivePet(int userId) {
        return null;
    }

    @Override
    public void updatePet(Connection c, int userId, OwnedPet pet) {
        throw new UnsupportedOperationException("Pets aren't kept in memory yet.");
    }

    @Override
    public void updateActivePet(Connection c, int userId, String petCode) {
        throw new UnsupportedOperationException("Pets aren't kept in memory yet.");
    }
}
