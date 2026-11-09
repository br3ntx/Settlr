package com.settlr.dao.mysql;

import com.settlr.dao.RecurringBillDao;
import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.ExpenseCategory;
import com.settlr.model.RecurringBill;
import com.settlr.util.Transaction;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * recurring_bills and recurring_bill_members in MySQL. Each bill is read together with its
 * member ids (one comma-separated column), and saved together with them in one transaction.
 */
public class MySqlRecurringBillDao extends AbstractDao<RecurringBill> implements RecurringBillDao {

    private static final String SELECT =
            "SELECT b.bill_id, b.household_id, b.title, b.category, b.amount, b.paid_by, b.due_day, "
          + "b.is_active, b.last_generated, b.created_by, b.created_at, "
          + "(SELECT GROUP_CONCAT(m.user_id ORDER BY m.user_id) FROM recurring_bill_members m "
          + " WHERE m.bill_id = b.bill_id) AS member_ids "
          + "FROM recurring_bills b ";

    @Override
    public RecurringBill findById(int id) throws DataAccessException {
        return queryOne(SELECT + "WHERE b.bill_id = ?", "Could not load the recurring bill.", id);
    }

    @Override
    public List<RecurringBill> findByHousehold(int householdId) throws DataAccessException {
        return queryList(SELECT + "WHERE b.household_id = ? ORDER BY b.bill_id",
                "Could not load the recurring bills.", householdId);
    }

    @Override
    public List<RecurringBill> findActive() throws DataAccessException {
        return queryList(SELECT + "WHERE b.is_active = TRUE ORDER BY b.bill_id", "Could not load the recurring bills.");
    }

    /** Saves the bill and its members together, so there's never a bill with nobody on it. */
    @Override
    public int insert(RecurringBill bill) throws DataAccessException {
        String message = "Could not save the recurring bill.";
        return inTransaction(message, c -> {
            int id = insert(c, "INSERT INTO recurring_bills (household_id, title, category, amount, paid_by, "
                            + "due_day, is_active, last_generated, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    message,
                    bill.getHouseholdId(), bill.getTitle(), bill.getCategory(), bill.getAmount(), bill.getPaidBy(),
                    bill.getDueDay(), bill.isActive(), bill.getLastGenerated(), bill.getCreatedBy());
            insertMembers(c, id, bill.getMemberIds(), message);
            return id;
        });
    }

    /** Updates the bill and replaces its member list, together. */
    @Override
    public void update(RecurringBill bill) throws DataAccessException {
        String message = "Could not update the recurring bill.";
        inTransaction(message, c -> {
            update(c, "UPDATE recurring_bills SET title = ?, category = ?, amount = ?, paid_by = ?, "
                            + "due_day = ?, is_active = ?, last_generated = ? WHERE bill_id = ?",
                    message,
                    bill.getTitle(), bill.getCategory(), bill.getAmount(), bill.getPaidBy(), bill.getDueDay(),
                    bill.isActive(), bill.getLastGenerated(), bill.getId());
            update(c, "DELETE FROM recurring_bill_members WHERE bill_id = ?", message, bill.getId());
            insertMembers(c, bill.getId(), bill.getMemberIds(), message);
            return null;
        });
    }

    /** Its member rows cascade. Fails if expenses were already made from the bill (no cascade there). */
    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM recurring_bills WHERE bill_id = ?", "Could not delete the recurring bill.", id);
    }

    @Override
    public void removeMemberFromBills(int householdId, int userId) throws DataAccessException {
        update("DELETE FROM recurring_bill_members WHERE user_id = ? AND bill_id IN "
                        + "(SELECT bill_id FROM recurring_bills WHERE household_id = ?)",
                "Could not update the recurring bills.", userId, householdId);
    }

    @Override
    public void updateLastGenerated(Connection c, int billId, LocalDate dueDate) throws DataAccessException {
        update(c, "UPDATE recurring_bills SET last_generated = ? WHERE bill_id = ?",
                "Could not update the recurring bill.", dueDate, billId);
    }

    @Override
    protected RecurringBill mapRow(ResultSet rs) throws SQLException {
        return new RecurringBill(
                rs.getInt("bill_id"),
                rs.getInt("household_id"),
                rs.getString("title"),
                ExpenseCategory.valueOf(rs.getString("category")),
                rs.getBigDecimal("amount"),
                rs.getInt("paid_by"),
                rs.getInt("due_day"),
                rs.getBoolean("is_active"),
                rs.getObject("last_generated", LocalDate.class), // null if never generated
                rs.getInt("created_by"),
                rs.getObject("created_at", LocalDateTime.class),
                parseIds(rs.getString("member_ids")));
    }

    // ---------- helpers ----------

    private void insertMembers(Connection c, int billId, List<Integer> memberIds, String message)
            throws DataAccessException {
        for (int userId : memberIds) {
            update(c, "INSERT INTO recurring_bill_members (bill_id, user_id) VALUES (?, ?)", message, billId, userId);
        }
    }

    /** "1,3,4" to [1, 3, 4]; null (no members) to an empty list. */
    private static List<Integer> parseIds(String csv) {
        List<Integer> ids = new ArrayList<>();
        if (csv != null && !csv.isEmpty()) {
            for (String part : csv.split(",")) {
                ids.add(Integer.parseInt(part.trim()));
            }
        }
        return ids;
    }

    /** Runs the work with Transaction; only DAO calls happen inside, so any error is a DataAccessException. */
    private static <T> T inTransaction(String message, Transaction.WorkWithResult<T> work) throws DataAccessException {
        try {
            return Transaction.get(message, work);
        } catch (DataAccessException e) {
            throw e;
        } catch (SettlrException e) {
            throw new DataAccessException(message, e);
        }
    }
}
