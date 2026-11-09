package com.settlr.service;

import com.settlr.exception.SettlrException;
import com.settlr.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Makes an older single-share payment (BalanceService.PaymentRow, from before settle ups
 * existed) look like a PendingPayment (Adapter pattern).
 */
public class SinglePaymentAdapter implements PendingPayment {

    private final BalanceService.PaymentRow row;
    private final PaymentService paymentService;

    public SinglePaymentAdapter(BalanceService.PaymentRow row, PaymentService paymentService) {
        this.row = row;
        this.paymentService = paymentService;
    }

    @Override
    public String getFromName() {
        return row.getFromUserName();
    }

    @Override
    public BigDecimal getAmount() {
        return row.getAmount();
    }

    @Override
    public LocalDateTime getSentAt() {
        return row.getPaidAt();
    }

    @Override
    public String getHeadline() {
        return getFromName() + " says they paid you " + MoneyUtil.format(getAmount());
    }

    @Override
    public String getDetails() {
        return "for " + row.getExpenseTitle();
    }

    @Override
    public String getConfirmQuestion() {
        return "Did " + getFromName() + " pay you " + MoneyUtil.format(getAmount()) + " for " + row.getExpenseTitle() + "?";
    }

    @Override
    public String getConfirmExplanation() {
        return "This marks their share of " + row.getExpenseTitle() + " as paid.";
    }

    @Override
    public String getConfirmedMessage() {
        return "Confirmed. " + getFromName() + "'s payment is now settled.";
    }

    @Override
    public String getRejectQuestion() {
        return "Mark " + getFromName() + "'s payment of " + MoneyUtil.format(getAmount())
                + " as not received? The share will stay unpaid.";
    }

    @Override
    public String getRejectedMessage() {
        return "Marked as not received. The share is still unpaid.";
    }

    @Override
    public void confirm() throws SettlrException {
        paymentService.confirm(row.getPaymentId());
    }

    @Override
    public void reject() throws SettlrException {
        paymentService.reject(row.getPaymentId());
    }
}
