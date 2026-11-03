package com.settlr.service;

import com.settlr.dao.ShopDao;
import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.model.TaskAssignment;
import com.settlr.model.User;
import com.settlr.util.DBConnection;
import com.settlr.util.DateTimeUtil;
import com.settlr.util.Session;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The pet on the Home page: which pets you have, which one you show, what each one wears,
 * and its mood. Items and pets are bought in the Shop (ShopService); this only arranges them.
 */
public class PetService {

    /** How the pet feels, worked out from the user's tasks and money. */
    public static final class Mood {
        private final boolean happy;
        private final String reason;

        Mood(boolean happy, String reason) {
            this.happy = happy;
            this.reason = reason;
        }

        public boolean isHappy() { return happy; }

        /** E.g. "you have 1 overdue task" or "you're all caught up". */
        public String getReason() { return reason; }
    }

    private final ShopDao shopDao = new ShopDao();
    private final TaskService taskService = new TaskService();
    private final SettlementService settlementService = new SettlementService();

    /** Your pets with their names and outfits, oldest first. */
    public List<OwnedPet> getMyPets() throws SettlrException {
        return shopDao.findPets(me().getId());
    }

    /** The code of the pet to show: the one you picked, or your first pet. Null if you have none. */
    public String getShownPetCode(List<OwnedPet> pets) throws SettlrException {
        if (pets.isEmpty()) {
            return null;
        }
        String active = shopDao.findActivePet(me().getId());
        for (OwnedPet p : pets) {
            if (p.getPet().getCode().equals(active)) {
                return active;
            }
        }
        return pets.get(0).getPet().getCode();
    }

    /**
     * Saves every pet's name and outfit and which one is shown, all together.
     * Only pets and items you own are accepted, each item in its own slot.
     */
    public void save(String shownPetCode, Collection<OwnedPet> pets) throws SettlrException {
        int userId = me().getId();
        Set<String> owned = shopDao.findOwnedCodes(userId);
        if (shownPetCode != null && !owned.contains(shownPetCode)) {
            throw new ValidationException("You don't have that pet.");
        }
        for (OwnedPet p : pets) {
            if (!owned.contains(p.getPet().getCode())) {
                throw new ValidationException("You don't have the " + p.getPet().getName() + ".");
            }
            if (p.getName() != null && p.getName().length() > OwnedPet.MAX_NAME_LENGTH) {
                throw new ValidationException("Pet names can be up to " + OwnedPet.MAX_NAME_LENGTH + " characters.");
            }
            for (Cosmetic.Slot slot : Cosmetic.Slot.values()) {
                Cosmetic item = p.getItem(slot);
                if (item != null && !owned.contains(item.getCode())) {
                    throw new ValidationException("You don't have the " + item.getName() + " yet.");
                }
            }
        }
        try (Connection c = DBConnection.getInstance().getConnection()) {
            c.setAutoCommit(false);
            try {
                for (OwnedPet p : pets) {
                    shopDao.updatePet(c, userId, p);
                }
                shopDao.updateActivePet(c, userId, shownPetCode);
                c.commit();
            } catch (SettlrException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save your pet. Please try again.", e);
        }
    }

    /**
     * Sad while you have an overdue task or owe money past its due date (and haven't sent it yet);
     * happy otherwise.
     */
    public Mood getMood() throws SettlrException {
        int myId = me().getId();
        int overdue = 0;
        for (TaskAssignment a : taskService.getOverdue()) {
            if (a.getAssigneeId() == myId) {
                overdue++;
            }
        }
        if (overdue > 0) {
            return new Mood(false, "you have " + overdue + (overdue == 1 ? " overdue task" : " overdue tasks"));
        }
        LocalDate today = LocalDate.now();
        for (SettlementService.Person person : settlementService.getPeople()) {
            if (person.getNet().signum() >= 0) {
                continue; // they owe you, or you're even
            }
            for (SettlementService.ShareLine line : person.getIOwe()) {
                if (!line.isPaymentPending() && line.getDueDate().isBefore(today)) {
                    return new Mood(false, "a bill you owe is past due");
                }
            }
        }
        return new Mood(true, "you're all caught up");
    }

    /** XP earned since Monday (tasks done and payments confirmed this week). */
    public int getXpThisWeek() throws SettlrException {
        return shopDao.findEarnedSince(me().getId(), DateTimeUtil.weekStart(LocalDate.now()));
    }

    private static User me() throws SettlrException {
        User u = Session.getInstance().getCurrentUser();
        if (u == null) {
            throw new ValidationException("Please log in again.");
        }
        return u;
    }
}
