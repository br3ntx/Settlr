package com.settlr.dao.mysql;

import com.settlr.dao.ActivityLogDao;
import com.settlr.dao.DaoFactory;
import com.settlr.dao.ExpenseDao;
import com.settlr.dao.ExpenseShareDao;
import com.settlr.dao.HouseholdDao;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.PaymentDao;
import com.settlr.dao.RecurringBillDao;
import com.settlr.dao.SettlementDao;
import com.settlr.dao.ShopDao;
import com.settlr.dao.SwapRequestDao;
import com.settlr.dao.TaskAssignmentDao;
import com.settlr.dao.TaskDao;
import com.settlr.dao.UserDao;

/** The family of DAOs that talk to MySQL. This is the one the app uses. */
public class MySqlDaoFactory extends DaoFactory {

    @Override public UserDao users() { return new MySqlUserDao(); }

    @Override public HouseholdDao households() { return new MySqlHouseholdDao(); }

    @Override public MembershipDao memberships() { return new MySqlMembershipDao(); }

    @Override public TaskDao tasks() { return new MySqlTaskDao(); }

    @Override public TaskAssignmentDao assignments() { return new MySqlTaskAssignmentDao(); }

    @Override public SwapRequestDao swaps() { return new MySqlSwapRequestDao(); }

    @Override public ExpenseDao expenses() { return new MySqlExpenseDao(); }

    @Override public ExpenseShareDao shares() { return new MySqlExpenseShareDao(); }

    @Override public PaymentDao payments() { return new MySqlPaymentDao(); }

    @Override public SettlementDao settlements() { return new MySqlSettlementDao(); }

    @Override public RecurringBillDao recurringBills() { return new MySqlRecurringBillDao(); }

    @Override public ActivityLogDao activityLog() { return new MySqlActivityLogDao(); }

    @Override public ShopDao shop() { return new MySqlShopDao(); }
}
