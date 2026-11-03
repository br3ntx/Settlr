package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import com.settlr.model.ShopItem;

/**
 * All SQL for the Shop and pets: owned_items (including each pet's name and outfit),
 * users.active_pet, xp_transactions, and the XP a user has earned.
 *
 * It doesn't implement Dao<T> because these rows have no id of their own (an owned item is
 * just "user X owns code Y"). Methods that take a Connection are for ShopService's
 * transactions, so paying and getting the item happen together or not at all.
 */
public class ShopDao {

    /** XP for each task point done on time / done late, and for each payment made on time. */
    public static final int XP_PER_POINT = 10;
    public static final int XP_PER_POINT_LATE = 5;
    public static final int XP_PER_PAYMENT = 20;

    /**
     * XP earned so far: every task finished (more when on time), and every payment the
     * user made that the other person confirmed and that was sent by the day after it was due.
     * Worked out from the tasks and payments themselves, so it can never drift out of step.
     */
    private static final String EARNED_SQL =
            "SELECT "
            + "(SELECT COALESCE(SUM(CASE a.status WHEN 'DONE' THEN t.points * " + XP_PER_POINT
            + " ELSE t.points * " + XP_PER_POINT_LATE + " END), 0) "
            + "   FROM task_assignments a JOIN tasks t ON t.task_id = a.task_id "
            + "  WHERE a.assignee_id = ? AND a.status IN ('DONE','DONE_LATE')) "
            + "+ (SELECT COUNT(*) * " + XP_PER_PAYMENT
            + "   FROM payments p "
            + "   JOIN expense_shares s ON s.share_id = p.share_id "
            + "   JOIN expenses e ON e.expense_id = s.expense_id "
            + "  WHERE p.from_user = ? AND p.status = 'CONFIRMED' "
            + "    AND p.paid_at < DATE_ADD(e.due_date, INTERVAL 1 DAY))";

    /** XP earned from tasks finished and payments confirmed since the given time (e.g. this week). */
    public int findEarnedSince(int userId, LocalDateTime since) throws DataAccessException {
        String sql = "SELECT "
                + "(SELECT COALESCE(SUM(CASE a.status WHEN 'DONE' THEN t.points * " + XP_PER_POINT
                + " ELSE t.points * " + XP_PER_POINT_LATE + " END), 0) "
                + "   FROM task_assignments a JOIN tasks t ON t.task_id = a.task_id "
                + "  WHERE a.assignee_id = ? AND a.status IN ('DONE','DONE_LATE') AND a.completed_at >= ?) "
                + "+ (SELECT COUNT(*) * " + XP_PER_PAYMENT
                + "   FROM payments p "
                + "   JOIN expense_shares s ON s.share_id = p.share_id "
                + "   JOIN expenses e ON e.expense_id = s.expense_id "
                + "  WHERE p.from_user = ? AND p.status = 'CONFIRMED' AND p.confirmed_at >= ? "
                + "    AND p.paid_at < DATE_ADD(e.due_date, INTERVAL 1 DAY))";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setTimestamp(2, Timestamp.valueOf(since));
            ps.setInt(3, userId);
            ps.setTimestamp(4, Timestamp.valueOf(since));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your XP.", ex);
        }
    }

    /** XP the user has right now: earned, minus what they spent, plus what they got back. */
    public int findBalance(int userId) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            return findBalance(c, userId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your XP.", ex);
        }
    }

    public int findBalance(Connection c, int userId) throws DataAccessException {
        String sql = "SELECT (" + EARNED_SQL + ") + "
                + "(SELECT COALESCE(SUM(amount), 0) FROM xp_transactions WHERE user_id = ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your XP.", ex);
        }
    }

    /** The codes of everything the user owns (hats and pets), oldest first. */
    public Set<String> findOwnedCodes(int userId) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            return findOwnedCodes(c, userId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your items.", ex);
        }
    }

    public Set<String> findOwnedCodes(Connection c, int userId) throws DataAccessException {
        String sql = "SELECT item_code FROM owned_items WHERE user_id = ? ORDER BY acquired_at, item_code";
        Set<String> codes = new LinkedHashSet<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    codes.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your items.", ex);
        }
        return codes;
    }

    /**
     * Locks the user's row until the transaction ends, so two purchases at the same time
     * (e.g. a double click) can't both spend the same XP.
     */
    public void lockUser(Connection c, int userId) throws DataAccessException {
        try (PreparedStatement ps = c.prepareStatement("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE")) {
            ps.setInt(1, userId);
            ps.executeQuery().close();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save your purchase.", ex);
        }
    }

    public void insertOwned(Connection c, int userId, String itemCode) throws DataAccessException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO owned_items (user_id, item_code) VALUES (?, ?)")) {
            ps.setInt(1, userId);
            ps.setString(2, itemCode);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save your purchase.", ex);
        }
    }

    /** Records XP spent (negative amount) or given back (positive). */
    public void insertTransaction(Connection c, int userId, int amount, String reason) throws DataAccessException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO xp_transactions (user_id, amount, reason) VALUES (?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setInt(2, amount);
            ps.setString(3, reason);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save your purchase.", ex);
        }
    }

    // ---- pets --------------------------------------------------------------

    /** The user's pets with their names and outfits, oldest first. Unknown codes are skipped. */
    public List<OwnedPet> findPets(int userId) throws DataAccessException {
        String sql = "SELECT item_code, pet_name, hat, glasses, neck FROM owned_items "
                + "WHERE user_id = ? AND item_code LIKE 'PET\\_%' ORDER BY acquired_at, item_code";
        List<OwnedPet> pets = new ArrayList<>();
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ShopItem item = ShopCatalog.find(rs.getString("item_code"));
                    if (!(item instanceof Pet)) {
                        continue; // a pet removed from the catalog
                    }
                    OwnedPet pet = new OwnedPet((Pet) item);
                    pet.setName(rs.getString("pet_name"));
                    for (String column : new String[] {"hat", "glasses", "neck"}) {
                        ShopItem worn = ShopCatalog.find(rs.getString(column));
                        if (worn instanceof Cosmetic) {
                            pet.wear((Cosmetic) worn);
                        }
                    }
                    pets.add(pet);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your pets.", ex);
        }
        return pets;
    }

    /** The code of the pet the user chose to show on Home, or null. */
    public String findActivePet(int userId) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT active_pet FROM users WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your pet.", ex);
        }
    }

    /** Saves a pet's name and outfit. */
    public void updatePet(Connection c, int userId, OwnedPet pet) throws DataAccessException {
        String sql = "UPDATE owned_items SET pet_name = ?, hat = ?, glasses = ?, neck = ? "
                + "WHERE user_id = ? AND item_code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pet.getName());
            ps.setString(2, codeOf(pet.getItem(Cosmetic.Slot.HAT)));
            ps.setString(3, codeOf(pet.getItem(Cosmetic.Slot.GLASSES)));
            ps.setString(4, codeOf(pet.getItem(Cosmetic.Slot.NECK)));
            ps.setInt(5, userId);
            ps.setString(6, pet.getPet().getCode());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save your pet.", ex);
        }
    }

    public void updateActivePet(Connection c, int userId, String petCode) throws DataAccessException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE users SET active_pet = ? WHERE user_id = ?")) {
            ps.setString(1, petCode);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save your pet.", ex);
        }
    }

    private static String codeOf(ShopItem item) {
        return item == null ? null : item.getCode();
    }
}
