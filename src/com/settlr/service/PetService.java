package com.settlr.service;

import com.settlr.dao.DaoFactory;
import com.settlr.dao.ShopDao;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.model.TaskAssignment;
import com.settlr.model.User;
import com.settlr.util.DateTimeUtil;
import com.settlr.util.Session;
import com.settlr.util.TransactionRunner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The household's pet on the Home page: which pets the household has, which one it shows, what
 * each one wears, and its mood. Everyone in the household sees the same pet, and any member can
 * rename it, dress it or switch it. Items and pets are bought in the Shop (ShopService); this
 * only arranges them.
 */
public class PetService {

    private final Session session;

    /** How the pet feels, worked out from the whole household's tasks and money. */
    public static final class Mood {
        private final boolean happy;
        private final String reason;

        Mood(boolean happy, String reason) {
            this.happy = happy;
            this.reason = reason;
        }

        public boolean isHappy() { return happy; }

        /** E.g. "Ben has 2 overdue tasks" or "everyone's caught up". */
        public String getReason() { return reason; }
    }

    private final ShopDao shopDao;
    private final TaskService taskService;
    private final SettlementService settlementService;
    private final TransactionRunner tx;

    /** Uses the app's MySQL DAOs and the logged-in Session. */
    public PetService() {
        this(DaoFactory.get(), Session.getInstance());
    }

    /**
     * Everything this service needs is passed in here (dependency injection), so a test can
     * give it in-memory DAOs instead of MySQL.
     */
    public PetService(DaoFactory daos, Session session) {
        this.session = session;
        this.tx = daos.transactions();
        this.shopDao = daos.shop();
        this.taskService = new TaskService(daos, session);
        this.settlementService = new SettlementService(daos, session);
    }

    /** The household's pets with their names and outfits, oldest first. */
    public List<OwnedPet> getPets() throws SettlrException {
        return shopDao.findPets(session.requireHouseholdId());
    }

    /** The code of the pet to show: the one the household picked, or its first pet. Null if it has none. */
    public String getShownPetCode(List<OwnedPet> pets) throws SettlrException {
        if (pets.isEmpty()) {
            return null;
        }
        String active = shopDao.findActivePet(session.requireHouseholdId());
        for (OwnedPet p : pets) {
            if (p.getPet().getCode().equals(active)) {
                return active;
            }
        }
        return pets.get(0).getPet().getCode();
    }

    /**
     * Saves every pet's name and outfit and which one is shown, all together. Any member can do
     * this, and everyone sees the change. Only pets and items the household owns are accepted,
     * each item in its own slot.
     */
    public void save(String shownPetCode, Collection<OwnedPet> pets) throws SettlrException {
        session.requireUser();
        int householdId = session.requireHouseholdId();
        Set<String> owned = shopDao.findOwnedCodes(householdId);
        if (shownPetCode != null && !owned.contains(shownPetCode)) {
            throw new ValidationException("Your household doesn't have that pet.");
        }
        for (OwnedPet p : pets) {
            if (!owned.contains(p.getPet().getCode())) {
                throw new ValidationException("Your household doesn't have the " + p.getPet().getName() + ".");
            }
            if (p.getName() != null && p.getName().length() > OwnedPet.MAX_NAME_LENGTH) {
                throw new ValidationException("Pet names can be up to " + OwnedPet.MAX_NAME_LENGTH + " characters.");
            }
            for (Cosmetic.Slot slot : Cosmetic.Slot.values()) {
                Cosmetic item = p.getItem(slot);
                if (item != null && !owned.contains(item.getCode())) {
                    throw new ValidationException("Your household doesn't have the " + item.getName() + " yet.");
                }
            }
        }
        tx.run("Could not save the pet. Please try again.", c -> {
            for (OwnedPet p : pets) {
                shopDao.updatePet(c, householdId, p);
            }
            shopDao.updateActivePet(c, householdId, shownPetCode);
        });
    }

    /**
     * Sad while anyone in the household has an overdue task, or anyone owes money past its due
     * date (and hasn't sent it yet); happy only when everyone is caught up. Overdue tasks are
     * named first, since ticking them off is the quickest fix.
     */
    public Mood getMood() throws SettlrException {
        int myId = session.requireUser().getId();
        Map<Integer, String> lateNames = new LinkedHashMap<>(); // who has overdue tasks, in due order
        Map<Integer, Integer> lateCounts = new HashMap<>();
        for (TaskAssignment a : taskService.getOverdue()) {
            lateNames.putIfAbsent(a.getAssigneeId(), a.getAssigneeName());
            lateCounts.merge(a.getAssigneeId(), 1, Integer::sum);
        }
        if (!lateNames.isEmpty()) {
            return new Mood(false, overdueReason(myId, lateNames, lateCounts));
        }
        List<User> owing = settlementService.getPastDueMembers();
        if (!owing.isEmpty()) {
            return new Mood(false, pastDueReason(myId, owing));
        }
        return new Mood(true, "everyone's caught up");
    }

    /** E.g. "you have 1 overdue task", "Ben has 2 overdue tasks" or "you and Ben have overdue tasks". */
    private static String overdueReason(int myId, Map<Integer, String> names, Map<Integer, Integer> counts) {
        if (names.size() == 1) {
            int who = names.keySet().iterator().next();
            int n = counts.get(who);
            String tasks = n + (n == 1 ? " overdue task" : " overdue tasks");
            return who == myId ? "you have " + tasks : names.get(who) + " has " + tasks;
        }
        return joinNames(myId, names) + " have overdue tasks";
    }

    /** E.g. "you owe a bill that's past due" or "Dani and Gio owe bills that are past due". */
    private static String pastDueReason(int myId, List<User> owing) {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (User u : owing) {
            names.put(u.getId(), u.getDisplayName());
        }
        if (names.size() == 1) {
            boolean me = names.containsKey(myId);
            return (me ? "you owe" : names.values().iterator().next() + " owes") + " a bill that's past due";
        }
        return joinNames(myId, names) + " owe bills that are past due";
    }

    /** "you and Ben", "Ben and Carlo" or "Ben, Carlo and 2 others": "you" always first. */
    private static String joinNames(int myId, Map<Integer, String> names) {
        List<String> list = new ArrayList<>();
        if (names.containsKey(myId)) {
            list.add("you");
        }
        names.forEach((id, name) -> {
            if (id != myId) {
                list.add(name);
            }
        });
        if (list.size() > 3) {
            return list.get(0) + ", " + list.get(1) + " and " + (list.size() - 2) + " others";
        }
        if (list.size() == 3) {
            return list.get(0) + ", " + list.get(1) + " and " + list.get(2);
        }
        return list.get(0) + " and " + list.get(1);
    }

    /** XP the household earned since Monday (tasks done and payments confirmed this week, by anyone). */
    public int getXpThisWeek() throws SettlrException {
        return XpRules.earned(shopDao.findXpActivity(session.requireHouseholdId(),
                DateTimeUtil.weekStart(LocalDate.now())));
    }

}
