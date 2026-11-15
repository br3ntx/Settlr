package com.settlr.dao.mysql;

import com.settlr.dao.ShopDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import com.settlr.model.ShopItem;
import com.settlr.model.XpActivity;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The Shop and pets in MySQL: owned_items, users.active_pet and xp_transactions. */
public class MySqlShopDao extends AbstractDao<OwnedPet> implements ShopDao {

    /**
     * Counts what earns XP: points of tasks done on time and late, and confirmed payments sent
     * by the day after they were due. The two ? of each part are the user id and, in %s,
     * an optional "since" condition. How much XP these are worth is decided by XpRules.
     */
    private static final String ACTIVITY =
            "SELECT "
          + "(SELECT COALESCE(SUM(CASE WHEN a.status = 'DONE' THEN t.points ELSE 0 END), 0) "
          + "   FROM task_assignments a JOIN tasks t ON t.task_id = a.task_id "
          + "  WHERE a.assignee_id = ? %1$s), "
          + "(SELECT COALESCE(SUM(CASE WHEN a.status = 'DONE_LATE' THEN t.points ELSE 0 END), 0) "
          + "   FROM task_assignments a JOIN tasks t ON t.task_id = a.task_id "
          + "  WHERE a.assignee_id = ? %1$s), "
          + "(SELECT COUNT(*) "
          + "   FROM payments p "
          + "   JOIN expense_shares s ON s.share_id = p.share_id "
          + "   JOIN expenses e ON e.expense_id = s.expense_id "
          + "  WHERE p.from_user = ? AND p.status = 'CONFIRMED' %2$s "
          + "    AND p.paid_at < DATE_ADD(e.due_date, INTERVAL 1 DAY))";

    private static final String ADJUSTMENTS =
            "SELECT COALESCE(SUM(amount), 0) FROM xp_transactions WHERE user_id = ?";

    @Override
    public XpActivity findXpActivity(int userId, LocalDateTime since) throws DataAccessException {
        if (since == null) {
            return withConnection("Could not load your XP.", c -> findXpActivity(c, userId));
        }
        String sql = String.format(ACTIVITY, "AND a.completed_at >= ?", "AND p.confirmed_at >= ?");
        return query(sql, MySqlShopDao::toActivity, "Could not load your XP.",
                userId, since, userId, since, userId, since).get(0);
    }

    /** The same counts over all time, inside the caller's transaction. */
    @Override
    public XpActivity findXpActivity(Connection c, int userId) throws DataAccessException {
        return query(c, String.format(ACTIVITY, "", ""), MySqlShopDao::toActivity, "Could not load your XP.",
                userId, userId, userId).get(0);
    }

    @Override
    public int findXpAdjustments(Connection c, int userId) throws DataAccessException {
        return query(c, ADJUSTMENTS, rs -> rs.getInt(1), "Could not load your XP.", userId).get(0);
    }

    @Override
    public int findXpAdjustments(int userId) throws DataAccessException {
        return withConnection("Could not load your XP.", c -> findXpAdjustments(c, userId));
    }

    /** Turns the three counts of the ACTIVITY query into an XpActivity. */
    private static XpActivity toActivity(ResultSet rs) throws SQLException {
        return new XpActivity(rs.getInt(1), rs.getInt(2), rs.getInt(3));
    }

    @Override
    public Set<String> findOwnedCodes(int userId) throws DataAccessException {
        return withConnection("Could not load your items.", c -> findOwnedCodes(c, userId));
    }

    @Override
    public Set<String> findOwnedCodes(Connection c, int userId) throws DataAccessException {
        return new LinkedHashSet<>(query(c, "SELECT item_code FROM owned_items WHERE user_id = ? "
                + "ORDER BY acquired_at, item_code", rs -> rs.getString(1), "Could not load your items.", userId));
    }

    @Override
    public void lockUser(Connection c, int userId) throws DataAccessException {
        query(c, "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE", rs -> null,
                "Could not save your purchase.", userId);
    }

    @Override
    public void insertOwned(Connection c, int userId, String itemCode) throws DataAccessException {
        update(c, "INSERT INTO owned_items (user_id, item_code) VALUES (?, ?)",
                "Could not save your purchase.", userId, itemCode);
    }

    @Override
    public void insertTransaction(Connection c, int userId, int amount, String reason) throws DataAccessException {
        update(c, "INSERT INTO xp_transactions (user_id, amount, reason) VALUES (?, ?, ?)",
                "Could not save your purchase.", userId, amount, reason);
    }

    /** Pets that were removed from the catalog are skipped. */
    @Override
    public List<OwnedPet> findPets(int userId) throws DataAccessException {
        List<OwnedPet> pets = new ArrayList<>();
        for (OwnedPet p : queryList("SELECT item_code, pet_name, hat, glasses FROM owned_items "
                        + "WHERE user_id = ? AND item_code LIKE 'PET\\_%' ORDER BY acquired_at, item_code",
                "Could not load your pets.", userId)) {
            if (p != null) {
                pets.add(p);
            }
        }
        return pets;
    }

    @Override
    public String findActivePet(int userId) throws DataAccessException {
        List<String> found = query("SELECT active_pet FROM users WHERE user_id = ?", rs -> rs.getString(1),
                "Could not load your pet.", userId);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public void updatePet(Connection c, int userId, OwnedPet pet) throws DataAccessException {
        update(c, "UPDATE owned_items SET pet_name = ?, hat = ?, glasses = ? "
                        + "WHERE user_id = ? AND item_code = ?",
                "Could not save your pet.",
                pet.getName(), codeOf(pet.getItem(Cosmetic.Slot.HAT)), codeOf(pet.getItem(Cosmetic.Slot.GLASSES)),
                userId, pet.getPet().getCode());
    }

    @Override
    public void updateActivePet(Connection c, int userId, String petCode) throws DataAccessException {
        update(c, "UPDATE users SET active_pet = ? WHERE user_id = ?", "Could not save your pet.", petCode, userId);
    }

    /** One owned pet row; null if that pet is no longer in the catalog. */
    @Override
    protected OwnedPet mapRow(ResultSet rs) throws SQLException {
        ShopItem item = ShopCatalog.find(rs.getString("item_code"));
        if (!(item instanceof Pet)) {
            return null;
        }
        OwnedPet pet = new OwnedPet((Pet) item);
        pet.setName(rs.getString("pet_name"));
        for (String column : new String[] {"hat", "glasses"}) {
            ShopItem worn = ShopCatalog.find(rs.getString(column));
            if (worn instanceof Cosmetic) {
                pet.wear((Cosmetic) worn);
            }
        }
        return pet;
    }

    private static String codeOf(ShopItem item) {
        return item == null ? null : item.getCode();
    }
}
