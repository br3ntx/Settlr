package com.settlr.service;

import com.settlr.model.PaymentStatus;
import com.settlr.model.Settlement;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Both kinds of payment look the same through PendingPayment (Adapter). Only the wording is
 * checked here; confirming needs the database.
 */
public class PendingPaymentTest {

    private final LocalDateTime sent = LocalDateTime.of(2026, 11, 9, 20, 0);

    @Test
    public void settleUpBecomesAPendingPayment() {
        PendingPayment p = new SettleUpAdapter(new SettlementService.Incoming(
                settlement("2400.00", null), "Dani", "Rent"), null);

        assertEquals("Dani", p.getFromName());
        assertEquals(new BigDecimal("2400.00"), p.getAmount());
        assertEquals(sent, p.getSentAt());
        assertTrue(p.getHeadline().startsWith("Dani says they paid you"));
        assertEquals("Rent", p.getDetails()); // what it clears, when there's no note
        assertTrue(p.getConfirmQuestion().startsWith("Did Dani pay you"));
    }

    @Test
    public void settleUpShowsTheirNoteWhenThereIsOne() {
        PendingPayment p = new SettleUpAdapter(new SettlementService.Incoming(
                settlement("500.00", "GCash ref 1234"), "Ben", "WiFi"), null);

        assertEquals("GCash ref 1234", p.getDetails());
    }

    @Test
    public void zeroSettleUpMeansYoureEven() {
        PendingPayment p = new SettleUpAdapter(new SettlementService.Incoming(
                settlement("0.00", null), "Ella", "Rent - Groceries"), null);

        assertEquals("Ella says you're even", p.getHeadline());
        assertEquals("Are you and Ella even?", p.getConfirmQuestion());
    }

    @Test
    public void singlePaymentBecomesAPendingPayment() {
        PendingPayment p = new SinglePaymentAdapter(new BalanceService.PaymentRow(
                12, "Carlo", "Groceries", new BigDecimal("300.00"), sent), null);

        assertEquals("Carlo", p.getFromName());
        assertEquals("for Groceries", p.getDetails());
        assertTrue(p.getConfirmQuestion().endsWith("for Groceries?"));
    }

    @Test
    public void bothKindsGoInOneList() {
        List<PendingPayment> waiting = Arrays.asList(
                new SettleUpAdapter(new SettlementService.Incoming(settlement("100.00", null), "Dani", "Rent"), null),
                new SinglePaymentAdapter(new BalanceService.PaymentRow(1, "Carlo", "Water",
                        new BigDecimal("50.00"), sent), null));

        BigDecimal total = BigDecimal.ZERO;
        for (PendingPayment p : waiting) {
            total = total.add(p.getAmount()); // no need to know which kind it is
        }
        assertEquals(new BigDecimal("150.00"), total);
    }

    private Settlement settlement(String amount, String note) {
        return new Settlement(3, 1, 4, 1, new BigDecimal(amount), note, sent, PaymentStatus.PENDING, null);
    }
}
