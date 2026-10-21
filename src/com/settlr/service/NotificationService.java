package com.settlr.service;

import com.settlr.dao.ExpenseShareDao;
import com.settlr.exception.AuthException;
import com.settlr.exception.SettlrException;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Payment;
import com.settlr.model.Reminders;
import com.settlr.model.TaskAssignment;
import com.settlr.util.Session;

import java.time.LocalDate;
import java.util.List;

/**
 * Collects what the login reminder pop-up shows. It owns no rules of its own: it asks the
 * services and DAOs that already know each answer and bundles the results into one Reminders object.
 *
 * <p>Why a separate class: the dialog (M7) then needs one call and one object, and the three
 * sources (tasks from M3, shares and payments from M4) stay behind this single door.
 */
public class NotificationService {

    private final TaskService taskService = new TaskService();
    private final PaymentService paymentService = new PaymentService();
    private final ExpenseShareDao expenseShareDao = new ExpenseShareDao();

    /**
     * Overdue tasks, unpaid shares past their due date, and payments waiting for the user to
     * confirm. If all three are empty the result's isEmpty() is true and no dialog should open.
     */
    public Reminders getLoginReminders() throws SettlrException {
        Session session = Session.getInstance();
        if (!session.isLoggedIn()) {
            throw new AuthException("Please log in first.");
        }
        int myId = session.getCurrentUser().getId();

        List<TaskAssignment> overdue = taskService.getMyOverdueTasks();
        List<ExpenseShare> pastDueShares = expenseShareDao.findUnsettledPastDue(myId, LocalDate.now());
        List<Payment> toConfirm = paymentService.getWaitingForMyConfirmation();

        return new Reminders(overdue, pastDueShares, toConfirm);
    }
}
