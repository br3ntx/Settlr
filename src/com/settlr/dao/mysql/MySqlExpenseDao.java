package com.settlr.dao.mysql;

import com.settlr.dao.ExpenseDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The expenses table in MySQL. */
public class MySqlExpenseDao extends AbstractDao<Expense> implements ExpenseDao {

    private static final String COLUMNS =
            "expense_id, household_id, bill_id, title, category, amount, paid_by, due_date, created_by, created_at";

    @Override
    public Expense findById(int id) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM expenses WHERE expense_id = ?",
                "Could not load the expense.", id);
    }

    @Override
    public int insert(Expense expense) throws DataAccessException {
        return withConnection("Could not save the expense.", c -> insert(c, expense));
    }

    /** created_at is left out so the database fills in the current time. bill_id is NULL for hand-made expenses. */
    @Override
    public int insert(Connection c, Expense e) throws DataAccessException {
        return insert(c, "INSERT INTO expenses (household_id, bill_id, title, category, amount, "
                        + "paid_by, due_date, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "Could not save the expense.",
                e.getHouseholdId(), e.getBillId(), e.getTitle(), e.getCategory(), e.getAmount(),
                e.getPaidBy(), e.getDueDate(), e.getCreatedBy());
    }

    /** Updates the editable fields. Household, bill and creator never change. */
    @Override
    public void update(Expense e) throws DataAccessException {
        update("UPDATE expenses SET title = ?, category = ?, amount = ?, paid_by = ?, due_date = ? "
                        + "WHERE expense_id = ?",
                "Could not update the expense.",
                e.getTitle(), e.getCategory(), e.getAmount(), e.getPaidBy(), e.getDueDate(), e.getId());
    }

    /** Its shares and their payments go too (ON DELETE CASCADE). */
    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM expenses WHERE expense_id = ?", "Could not delete the expense.", id);
    }

    @Override
    public List<Expense> findByHousehold(int householdId, ExpenseCategory filterOrNull) throws DataAccessException {
        String order = " ORDER BY created_at DESC, expense_id DESC";
        if (filterOrNull == null) {
            return queryList("SELECT " + COLUMNS + " FROM expenses WHERE household_id = ?" + order,
                    "Could not load the expenses.", householdId);
        }
        return queryList("SELECT " + COLUMNS + " FROM expenses WHERE household_id = ? AND category = ?" + order,
                "Could not load the expenses.", householdId, filterOrNull);
    }

    @Override
    public boolean existsForBill(int billId, LocalDate dueDate) throws DataAccessException {
        return exists("SELECT 1 FROM expenses WHERE bill_id = ? AND due_date = ? LIMIT 1",
                "Could not check the recurring bill.", billId, dueDate);
    }

    @Override
    protected Expense mapRow(ResultSet rs) throws SQLException {
        return Expense.builder()
                .id(rs.getInt("expense_id"))
                .household(rs.getInt("household_id"))
                .fromBill(rs.getObject("bill_id", Integer.class)) // null stays null
                .title(rs.getString("title"))
                .category(ExpenseCategory.valueOf(rs.getString("category")))
                .amount(rs.getBigDecimal("amount"))
                .paidBy(rs.getInt("paid_by"))
                .dueDate(rs.getObject("due_date", LocalDate.class))
                .createdBy(rs.getInt("created_by"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .build();
    }
}
