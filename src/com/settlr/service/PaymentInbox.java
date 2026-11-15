package com.settlr.service;

import com.settlr.dao.DaoFactory;
import com.settlr.exception.SettlrException;
import com.settlr.util.Session;

import java.util.ArrayList;
import java.util.List;

/**
 * Every payment waiting for the current user to confirm, both kinds in one list (settle ups
 * first, then older single payments, each oldest first). Each item is wrapped in its adapter,
 * so callers only deal with PendingPayment.
 */
public class PaymentInbox {

    private final SettlementService settlementService;
    private final BalanceService balanceService;
    private final PaymentService paymentService;

    /** Uses the app's MySQL DAOs and the logged-in Session. */
    public PaymentInbox() {
        this(DaoFactory.get(), Session.getInstance());
    }

    /**
     * Everything this service needs is passed in here (dependency injection), so a test can
     * give it in-memory DAOs instead of MySQL.
     */
    public PaymentInbox(DaoFactory daos, Session session) {
        this.settlementService = new SettlementService(daos, session);
        this.balanceService = new BalanceService(daos, session);
        this.paymentService = new PaymentService(daos, session);
    }

    public List<PendingPayment> getWaitingForMe() throws SettlrException {
        List<PendingPayment> all = new ArrayList<>();
        for (SettlementService.Incoming in : settlementService.getWaitingForMe()) {
            all.add(new SettleUpAdapter(in, settlementService));
        }
        for (BalanceService.PaymentRow row : balanceService.getIncomingPaymentRows()) {
            all.add(new SinglePaymentAdapter(row, paymentService));
        }
        return all;
    }
}
