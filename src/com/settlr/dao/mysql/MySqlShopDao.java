package com.settlr.dao.mysql;

import com.settlr.dao.ShopDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import com.settlr.model.ShopItem;

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

    /** XP from finished tasks (more when on time). Its two ? are the user id (and an optional time). */
    private static final String TASK_XP =
            "(SELECT COALESCE(SUM(CASE a.status WHEN 'DONE' THEN t.points * " + XP_PER_POINT
          + " ELSE t.points * " + XP_PER_POINT_LATE + " END), 0) "
          + "   FROM task_assignments a JOIN tasks t ON t.task_id = a.task_id "
          + "  WHERE a.assignee_id = ? AND a.status IN ('DONE','DONE_LATE') %s)";

    /** XP from payments the other person confirmed, sent by the day after they were due. */
    private static final String PAYMENT_XP =
            "(SELECT COUNT(*) * " + XP_PER_PAYMENT
          + "   FROM payments p "
          + "   JOIN expense_shares s ON s.share_id = p.share_id "
          + "   JOIN expenses e ON e.expense_id = s.expense_id "
          + "  WHERE p.from_user = ? AND p.status = 'CONFIRMED' %s "
          + "    AND p.paid_at < DATE_ADD(e.due_date, INTERVAL 1 DAY))";

    /** Worked out from the tasks and payments themselves, so it can never drift out of step. */
    private static final String EARNED = String.format(TASK_XP, "") + " + " + String.format(PAYMENT_XP, "");

    @Override
    public int findEarnedSince(int userId, LocalDateTime since) throws DataAccessException {
        String sql = "SELECT " + String.format(TASK_XP, "AND a.completed_at >= ?")
                + " + " + String.format(PAYMENT_XP, "AND p.confirmed_at >= ?");
        return query(sql, rs -> rs.getInt(1), "Could not load your XP.", userId, since, userId, since).get(0);
    }

    @Override
    public int findBalance(int userId) throws DataAccessException {
        return withConnection("Could not load your XP.", c -> findBalance(c, userId));
    }

    @Override
    public int findBalance(Connection c, int userId) throws DataAccessException {
        String sql = "SELECT " + EARNED + " + (SELECT COALESCE(SUM(amount), 0) FROM xp_transactions WHERE user_id = ?)";
        return query(c, sql, rs -> rs.getInt(1), "Could not load your XP.", userId, userId, userId).get(0);
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
        for (OwnedPet p : queryList("SELECT item_code, pet_name, hat, glasses, neck FROM owned_items "
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
        update(c, "UPDATE owned_items SET pet_name = ?, hat = ?, glasses = ?, neck = ? "
                        + "WHERE user_id = ? AND item_code = ?",
                "Could not save your pet.",
                pet.getName(), codeOf(pet.getItem(Cosmetic.Slot.HAT)), codeOf(pet.getItem(Cosmetic.Slot.GLASSES)),
                codeOf(pet.getItem(Cosmetic.Slot.NECK)), userId, pet.getPet().getCode());
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
        for (String column : new String[] {"hat", "glasses", "neck"}) {
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
