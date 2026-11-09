package com.settlr.model;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

/** Expense.builder() fills in every value by name, and build() still checks them. */
public class ExpenseBuilderTest {

    @Test
    public void buildsAnExpenseWithNamedValues() {
        Expense e = Expense.builder()
                .household(1)
                .title("Pizza party")
                .category(ExpenseCategory.RENT)
                .amount(new BigDecimal("900.00"))
                .paidBy(2)
                .dueDate(LocalDate.of(2026, 11, 20))
                .createdBy(2)
                .build();

        assertEquals("Pizza party", e.getTitle());
        assertEquals(2, e.getPaidBy());
        assertEquals(0, e.getId());     // not saved yet
        assertNull(e.getBillId());      // typed in by hand, not from a recurring bill
    }

    @Test
    public void buildStillChecksTheValues() {
        assertThrows(IllegalArgumentException.class, () -> Expense.builder()
                .household(1).category(ExpenseCategory.RENT).amount(new BigDecimal("900.00"))
                .paidBy(2).dueDate(LocalDate.now()).createdBy(2)
                .build()); // no title
    }

    @Test
    public void ownedPetCopyIsIndependent() {
        OwnedPet original = new OwnedPet((Pet) ShopCatalog.find("PET_DOG"));
        OwnedPet copy = original.copy(); // Prototype
        copy.setName("Mochi");
        copy.wear((Cosmetic) ShopCatalog.find("HAT_CROWN"));

        assertEquals("Dog", original.getDisplayName());
        assertNull(original.getItem(Cosmetic.Slot.HAT));
        assertEquals("Mochi", copy.getDisplayName());
    }
}
