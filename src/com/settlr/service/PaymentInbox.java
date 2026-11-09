package com.settlr.service;

import com.settlr.exception.SettlrException;

import java.util.ArrayList;
import java.util.List;

/**
 * Every payment waiting for the current user to confirm, both kinds in one list (settle ups
 * first, then older single payments, each oldest first). Each item is wrapped in its adapter,
 * so callers only deal with PendingPayment.
 */
public class PaymentInbox {

    private final SettlementService settlementService = new SettlementService();
    private final BalanceService balanceService = new BalanceService();
    private final PaymentService paymentService = new PaymentService();

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
