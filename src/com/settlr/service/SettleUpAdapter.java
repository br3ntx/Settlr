package com.settlr.service;

import com.settlr.exception.SettlrException;
import com.settlr.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Makes a settle up (SettlementService.Incoming) look like a PendingPayment (Adapter pattern). */
public class SettleUpAdapter implements PendingPayment {

    private final SettlementService.Incoming incoming;
    private final SettlementService settlementService;

    public SettleUpAdapter(SettlementService.Incoming incoming, SettlementService settlementService) {
        this.incoming = incoming;
        this.settlementService = settlementService;
    }

    @Override
    public String getFromName() {
        return incoming.getFromName();
    }

    @Override
    public BigDecimal getAmount() {
        return incoming.getSettlement().getAmount();
    }

    @Override
    public LocalDateTime getSentAt() {
        return incoming.getSettlement().getCreatedAt();
    }

    private boolean even() {
        return getAmount().signum() == 0;
    }

    @Override
    public String getHeadline() {
        return even() ? getFromName() + " says you're even"
                : getFromName() + " says they paid you " + MoneyUtil.format(getAmount());
    }

    /** Their note if they wrote one, otherwise what it clears (e.g. "Rent - Groceries"). */
    @Override
    public String getDetails() {
        String note = incoming.getSettlement().getNote();
        return note == null || note.isBlank() ? incoming.getSummary() : note;
    }

    @Override
    public String getConfirmQuestion() {
        return even() ? "Are you and " + getFromName() + " even?"
                : "Did " + getFromName() + " pay you " + MoneyUtil.format(getAmount()) + "?";
    }

    @Override
    public String getConfirmExplanation() {
        return "This settles everything between you two.";
    }

    @Override
    public String getConfirmedMessage() {
        return "Confirmed. You and " + getFromName() + " are settled up.";
    }

    @Override
    public String getRejectQuestion() {
        return "Mark it as not received? Nothing will be settled, and " + getFromName() + " can send it again.";
    }

    @Override
    public String getRejectedMessage() {
        return "Marked as not received. Nothing was settled.";
    }

    @Override
    public void confirm() throws SettlrException {
        settlementService.confirm(incoming.getSettlement().getId());
    }

    @Override
    public void reject() throws SettlrException {
        settlementService.reject(incoming.getSettlement().getId());
    }
}
