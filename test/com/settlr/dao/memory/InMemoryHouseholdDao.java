package com.settlr.dao.memory;

import com.settlr.dao.HouseholdDao;
import com.settlr.model.Household;

/** Households kept in a list. */
class InMemoryHouseholdDao implements HouseholdDao {

    private final InMemoryDatabase db;

    InMemoryHouseholdDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Household findById(int id) {
        for (Household h : db.households) {
            if (h.getId() == id) {
                return h;
            }
        }
        return null;
    }

    @Override
    public Household findByInviteCode(String code) {
        for (Household h : db.households) {
            if (h.getInviteCode().equalsIgnoreCase(code)) {
                return h;
            }
        }
        return null;
    }

    /** Keeps the id the test gave the household, or hands out a new one if it's 0. */
    @Override
    public int insert(Household h) {
        if (h.getId() <= 0) {
            h.setId(db.newId());
        }
        db.households.add(h);
        return h.getId();
    }

    @Override
    public void update(Household h) {
        delete(h.getId());
        db.households.add(h);
    }

    @Override
    public void delete(int id) {
        db.households.removeIf(h -> h.getId() == id);
    }
}
