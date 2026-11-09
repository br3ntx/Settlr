package com.settlr.service;

import com.settlr.dao.DaoFactory;
import com.settlr.dao.ShopDao;
import com.settlr.exception.NotFoundException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.Cosmetic;
import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import com.settlr.model.ShopItem;
import com.settlr.util.Transaction;
import com.settlr.util.Session;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The Shop's rules: what things cost, buying a hat or accessory, and opening the pet egg.
 * XP is earned by finishing tasks and paying on time (see ShopDao), and spent here.
 * Every purchase checks the balance and saves in one transaction.
 */
public class ShopService {

    private final Session session = Session.getInstance();

    private static final String SAVE_FAILED = "Could not save your purchase. Please try again.";

    /** What hatched from a pet egg. */
    public static final class HatchResult {
        private final Pet pet;
        private final boolean duplicate;
        private final int balanceAfter;

        HatchResult(Pet pet, boolean duplicate, int balanceAfter) {
            this.pet = pet;
            this.duplicate = duplicate;
            this.balanceAfter = balanceAfter;
        }

        public Pet getPet() { return pet; }

        /** True when the user already had this pet; they got ShopCatalog.DUPLICATE_REFUND XP back. */
        public boolean isDuplicate() { return duplicate; }

        public int getBalanceAfter() { return balanceAfter; }
    }

    private final ShopDao shopDao = DaoFactory.get().shop();
    private final Random random;

    public ShopService() {
        this(new SecureRandom());
    }

    /** For tests: pass a seeded Random to get the same egg results every time. */
    ShopService(Random random) {
        this.random = random;
    }

    /** The current user's XP. */
    public int getBalance() throws SettlrException {
        return shopDao.findBalance(session.requireUser().getId());
    }

    /** Codes of the hats and pets the current user owns. */
    public Set<String> getOwnedCodes() throws SettlrException {
        return shopDao.findOwnedCodes(session.requireUser().getId());
    }

    /** Buys a hat or accessory. Returns the XP left. */
    public int buy(String itemCode) throws SettlrException {
        ShopItem item = ShopCatalog.find(itemCode);
        if (!(item instanceof Cosmetic)) {
            throw new NotFoundException("That item isn't in the Shop.");
        }
        Cosmetic cosmetic = (Cosmetic) item;
        int userId = session.requireUser().getId();
        return Transaction.get(SAVE_FAILED, c -> {
            shopDao.lockUser(c, userId);
            if (shopDao.findOwnedCodes(c, userId).contains(cosmetic.getCode())) {
                throw new ValidationException("You already have the " + cosmetic.getName() + ".");
            }
            int balance = shopDao.findBalance(c, userId);
            requireEnough(balance, cosmetic.getPrice());
            shopDao.insertTransaction(c, userId, -cosmetic.getPrice(), "Bought " + cosmetic.getName());
            shopDao.insertOwned(c, userId, cosmetic.getCode());
            return balance - cosmetic.getPrice();
        });
    }

    /**
     * Hatches a pet egg: picks a rarity by its weight, then a pet of that rarity.
     * A pet you already have gives half the price back instead.
     */
    public HatchResult hatchEgg() throws SettlrException {
        int userId = session.requireUser().getId();
        Pet pet = rollPet();
        return Transaction.get(SAVE_FAILED, c -> {
            shopDao.lockUser(c, userId);
            int balance = shopDao.findBalance(c, userId);
            requireEnough(balance, ShopCatalog.EGG_PRICE);
            shopDao.insertTransaction(c, userId, -ShopCatalog.EGG_PRICE, "Hatched a pet egg");
            boolean duplicate = shopDao.findOwnedCodes(c, userId).contains(pet.getCode());
            int after = balance - ShopCatalog.EGG_PRICE;
            if (duplicate) {
                shopDao.insertTransaction(c, userId, ShopCatalog.DUPLICATE_REFUND,
                        "Already had the " + pet.getName());
                after += ShopCatalog.DUPLICATE_REFUND;
            } else {
                shopDao.insertOwned(c, userId, pet.getCode());
            }
            return new HatchResult(pet, duplicate, after);
        });
    }

    /** Chance of each rarity, as a percent, for the odds line. */
    public static int chanceOf(Pet.Rarity rarity) {
        int total = 0;
        for (Pet.Rarity r : Pet.Rarity.values()) {
            total += r.getWeight();
        }
        return Math.round(100f * rarity.getWeight() / total);
    }

    /** Picks a rarity by its weight, then a pet of that rarity. Package-private so the tests can call it. */
    Pet rollPet() {
        int total = 0;
        for (Pet.Rarity r : Pet.Rarity.values()) {
            total += r.getWeight();
        }
        int roll = random.nextInt(total);
        Pet.Rarity picked = Pet.Rarity.COMMON;
        for (Pet.Rarity r : Pet.Rarity.values()) {
            if (roll < r.getWeight()) {
                picked = r;
                break;
            }
            roll -= r.getWeight();
        }
        List<Pet> pool = ShopCatalog.pets(picked);
        if (pool.isEmpty()) { // a rarity with no pets yet: fall back to any pet
            pool = ShopCatalog.pets();
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private static void requireEnough(int balance, int price) throws ValidationException {
        if (balance < price) {
            throw new ValidationException("You need " + (price - balance) + " more XP.");
        }
    }

}
