package com.settlr.dao.memory;

import com.settlr.dao.ShopDao;
import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import com.settlr.model.ShopItem;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.XpActivity;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Shop in memory, per household like the real one. XP activity is counted from the
 * household's in-memory task turns (payments aren't counted here), so tests can check the XP
 * rules end to end without MySQL.
 */
class InMemoryShopDao implements ShopDao {

    private final InMemoryDatabase db;

    InMemoryShopDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public XpActivity findXpActivity(int householdId, LocalDateTime since) {
        int onTime = 0;
        int late = 0;
        for (TaskAssignment a : db.assignments) {
            Task task = db.task(a.getTaskId());
            if (task == null || task.getHouseholdId() != householdId || a.getCompletedAt() == null
                    || (since != null && a.getCompletedAt().isBefore(since))) {
                continue;
            }
            if (a.getStatus() == TaskStatus.DONE) {
                onTime += task.getPoints();
            } else if (a.getStatus() == TaskStatus.DONE_LATE) {
                late += task.getPoints();
            }
        }
        return new XpActivity(onTime, late, 0);
    }

    /** The same counts over all time (in memory there is no Connection to use). */
    @Override
    public XpActivity findXpActivity(Connection c, int householdId) {
        return findXpActivity(householdId, null);
    }

    @Override
    public int findXpAdjustments(Connection c, int householdId) {
        return findXpAdjustments(householdId);
    }

    @Override
    public int findXpAdjustments(int householdId) {
        int total = 0;
        for (int[] tx : db.xpTransactions) {
            if (tx[0] == householdId) {
                total += tx[2];
            }
        }
        return total;
    }

    @Override
    public Set<String> findOwnedCodes(int householdId) {
        return new LinkedHashSet<>(items(householdId).keySet());
    }

    @Override
    public Set<String> findOwnedCodes(Connection c, int householdId) {
        return findOwnedCodes(householdId);
    }

    @Override
    public void lockHousehold(Connection c, int householdId) {
        // one test thread: nothing to lock
    }

    @Override
    public void insertOwned(Connection c, int householdId, String itemCode) {
        items(householdId).put(itemCode, new String[3]); // {pet name, hat, glasses}
    }

    @Override
    public void insertTransaction(Connection c, int householdId, int userId, int amount, String reason) {
        db.xpTransactions.add(new int[] {householdId, userId, amount});
    }

    /** Pets that aren't in the catalog are skipped, like in MySQL. */
    @Override
    public List<OwnedPet> findPets(int householdId) {
        List<OwnedPet> pets = new ArrayList<>();
        items(householdId).forEach((code, row) -> {
            if (ShopCatalog.find(code) instanceof Pet) {
                OwnedPet pet = new OwnedPet((Pet) ShopCatalog.find(code));
                pet.setName(row[0]);
                for (int i = 1; i <= 2; i++) {
                    if (ShopCatalog.find(row[i]) instanceof Cosmetic) {
                        pet.wear((Cosmetic) ShopCatalog.find(row[i]));
                    }
                }
                pets.add(pet);
            }
        });
        return pets;
    }

    @Override
    public String findActivePet(int householdId) {
        return db.activePets.get(householdId);
    }

    @Override
    public void updatePet(Connection c, int householdId, OwnedPet pet) {
        String[] row = items(householdId).get(pet.getPet().getCode());
        if (row != null) {
            row[0] = pet.getName();
            row[1] = codeOf(pet.getItem(Cosmetic.Slot.HAT));
            row[2] = codeOf(pet.getItem(Cosmetic.Slot.GLASSES));
        }
    }

    @Override
    public void updateActivePet(Connection c, int householdId, String petCode) {
        db.activePets.put(householdId, petCode);
    }

    /** The household's items in the order they were bought: item code -> {pet name, hat, glasses}. */
    private Map<String, String[]> items(int householdId) {
        return db.owned.computeIfAbsent(householdId, k -> new LinkedHashMap<>());
    }

    private static String codeOf(ShopItem item) {
        return item == null ? null : item.getCode();
    }
}
