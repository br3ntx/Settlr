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

/** The Shop and pets in MySQL: owned_items, households.active_pet and xp_transactions, all per household. */
public class MySqlShopDao extends AbstractDao<OwnedPet> implements ShopDao {

    /**
     * Counts what earns the household XP: points of its tasks done on time and late (by anyone),
     * and confirmed payments for its expenses sent by the day after they were due. The ? of each
     * part is the household id and, in %s, an optional "since" condition. How much XP these are
     * worth is decided by XpRules.
     */
    private static final String ACTIVITY =
            "SELECT "
          + "(SELECT COALESCE(SUM(CASE WHEN a.status = 'DONE' THEN t.points ELSE 0 END), 0) "
          + "   FROM task_assignments a JOIN tasks t ON t.task_id = a.task_id "
          + "  WHERE t.household_id = ? %1$s), "
          + "(SELECT COALESCE(SUM(CASE WHEN a.status = 'DONE_LATE' THEN t.points ELSE 0 END), 0) "
          + "   FROM task_assignments a JOIN tasks t ON t.task_id = a.task_id "
          + "  WHERE t.household_id = ? %1$s), "
          + "(SELECT COUNT(*) "
          + "   FROM payments p "
          + "   JOIN expense_shares s ON s.share_id = p.share_id "
          + "   JOIN expenses e ON e.expense_id = s.expense_id "
          + "  WHERE e.household_id = ? AND p.status = 'CONFIRMED' %2$s "
          + "    AND p.paid_at < DATE_ADD(e.due_date, INTERVAL 1 DAY))";

    private static final String ADJUSTMENTS =
            "SELECT COALESCE(SUM(amount), 0) FROM xp_transactions WHERE household_id = ?";

    @Override
    public XpActivity findXpActivity(int householdId, LocalDateTime since) throws DataAccessException {
        if (since == null) {
            return withConnection("Could not load the XP.", c -> findXpActivity(c, householdId));
        }
        String sql = String.format(ACTIVITY, "AND a.completed_at >= ?", "AND p.confirmed_at >= ?");
        return query(sql, MySqlShopDao::toActivity, "Could not load the XP.",
                householdId, since, householdId, since, householdId, since).get(0);
    }

    /** The same counts over all time, inside the caller's transaction. */
    @Override
    public XpActivity findXpActivity(Connection c, int householdId) throws DataAccessException {
        return query(c, String.format(ACTIVITY, "", ""), MySqlShopDao::toActivity, "Could not load the XP.",
                householdId, householdId, householdId).get(0);
    }

    @Override
    public int findXpAdjustments(Connection c, int householdId) throws DataAccessException {
        return query(c, ADJUSTMENTS, rs -> rs.getInt(1), "Could not load the XP.", householdId).get(0);
    }

    @Override
    public int findXpAdjustments(int householdId) throws DataAccessException {
        return withConnection("Could not load the XP.", c -> findXpAdjustments(c, householdId));
    }

    /** Turns the three counts of the ACTIVITY query into an XpActivity. */
    private static XpActivity toActivity(ResultSet rs) throws SQLException {
        return new XpActivity(rs.getInt(1), rs.getInt(2), rs.getInt(3));
    }

    @Override
    public Set<String> findOwnedCodes(int householdId) throws DataAccessException {
        return withConnection("Could not load the items.", c -> findOwnedCodes(c, householdId));
    }

    @Override
    public Set<String> findOwnedCodes(Connection c, int householdId) throws DataAccessException {
        return new LinkedHashSet<>(query(c, "SELECT item_code FROM owned_items WHERE household_id = ? "
                + "ORDER BY acquired_at, item_code", rs -> rs.getString(1), "Could not load the items.", householdId));
    }

    @Override
    public void lockHousehold(Connection c, int householdId) throws DataAccessException {
        query(c, "SELECT household_id FROM households WHERE household_id = ? FOR UPDATE", rs -> null,
                "Could not save your purchase.", householdId);
    }

    @Override
    public void insertOwned(Connection c, int householdId, String itemCode) throws DataAccessException {
        update(c, "INSERT INTO owned_items (household_id, item_code) VALUES (?, ?)",
                "Could not save your purchase.", householdId, itemCode);
    }

    @Override
    public void insertTransaction(Connection c, int householdId, int userId, int amount, String reason)
            throws DataAccessException {
        update(c, "INSERT INTO xp_transactions (household_id, user_id, amount, reason) VALUES (?, ?, ?, ?)",
                "Could not save your purchase.", householdId, userId, amount, reason);
    }

    /** Pets that were removed from the catalog are skipped. */
    @Override
    public List<OwnedPet> findPets(int householdId) throws DataAccessException {
        List<OwnedPet> pets = new ArrayList<>();
        for (OwnedPet p : queryList("SELECT item_code, pet_name, hat, glasses FROM owned_items "
                        + "WHERE household_id = ? AND item_code LIKE 'PET\\_%' ORDER BY acquired_at, item_code",
                "Could not load the pets.", householdId)) {
            if (p != null) {
                pets.add(p);
            }
        }
        return pets;
    }

    @Override
    public String findActivePet(int householdId) throws DataAccessException {
        List<String> found = query("SELECT active_pet FROM households WHERE household_id = ?",
                rs -> rs.getString(1), "Could not load the pet.", householdId);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public void updatePet(Connection c, int householdId, OwnedPet pet) throws DataAccessException {
        update(c, "UPDATE owned_items SET pet_name = ?, hat = ?, glasses = ? "
                        + "WHERE household_id = ? AND item_code = ?",
                "Could not save the pet.",
                pet.getName(), codeOf(pet.getItem(Cosmetic.Slot.HAT)), codeOf(pet.getItem(Cosmetic.Slot.GLASSES)),
                householdId, pet.getPet().getCode());
    }

    @Override
    public void updateActivePet(Connection c, int householdId, String petCode) throws DataAccessException {
        update(c, "UPDATE households SET active_pet = ? WHERE household_id = ?", "Could not save the pet.",
                petCode, householdId);
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
