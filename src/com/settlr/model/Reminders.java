package com.settlr.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the login pop-up may need to show: overdue tasks, unpaid shares past their
 * due date, and payments waiting for the user's confirmation (master doc 3.8).
 *
 * <p>It only carries the three lists; NotificationService fills it and ReminderDialog (M7) shows it.
 * The lists are copied and handed out read-only so a screen cannot change the data by accident.
 */
public class Reminders {

    private List<TaskAssignment> overdueTasks;
    private List<ExpenseShare> unpaidPastDueShares;
    private List<Payment> paymentsToConfirm;

    public Reminders(List<TaskAssignment> overdueTasks,
                     List<ExpenseShare> unpaidPastDueShares,
                     List<Payment> paymentsToConfirm) {
        setOverdueTasks(overdueTasks);
        setUnpaidPastDueShares(unpaidPastDueShares);
        setPaymentsToConfirm(paymentsToConfirm);
    }

    public List<TaskAssignment> getOverdueTasks() {
        return Collections.unmodifiableList(overdueTasks);
    }

    public void setOverdueTasks(List<TaskAssignment> overdueTasks) {
        if (overdueTasks == null) {
            throw new IllegalArgumentException("The overdue task list cannot be null.");
        }
        this.overdueTasks = new ArrayList<>(overdueTasks);
    }

    public List<ExpenseShare> getUnpaidPastDueShares() {
        return Collections.unmodifiableList(unpaidPastDueShares);
    }

    public void setUnpaidPastDueShares(List<ExpenseShare> unpaidPastDueShares) {
        if (unpaidPastDueShares == null) {
            throw new IllegalArgumentException("The unpaid share list cannot be null.");
        }
        this.unpaidPastDueShares = new ArrayList<>(unpaidPastDueShares);
    }

    public List<Payment> getPaymentsToConfirm() {
        return Collections.unmodifiableList(paymentsToConfirm);
    }

    public void setPaymentsToConfirm(List<Payment> paymentsToConfirm) {
        if (paymentsToConfirm == null) {
            throw new IllegalArgumentException("The payment list cannot be null.");
        }
        this.paymentsToConfirm = new ArrayList<>(paymentsToConfirm);
    }

    /** True when there is nothing to remind about, so no dialog should be shown. */
    public boolean isEmpty() {
        return overdueTasks.isEmpty() && unpaidPastDueShares.isEmpty() && paymentsToConfirm.isEmpty();
    }

    /** Total number of items, handy for a title like "3 things need your attention". */
    public int getTotalCount() {
        return overdueTasks.size() + unpaidPastDueShares.size() + paymentsToConfirm.size();
    }
}
