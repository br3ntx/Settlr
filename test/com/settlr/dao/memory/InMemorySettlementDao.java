package com.settlr.dao.memory;

import com.settlr.dao.SettlementDao;
import com.settlr.model.Settlement;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/** Settle ups kept in a list. */
class InMemorySettlementDao implements SettlementDao {

    private final InMemoryDatabase db;

    InMemorySettlementDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Settlement findById(int id) {
        for (Settlement s : db.settlements) {
            if (s.getId() == id) {
                return s;
            }
        }
        return null;
    }

    @Override
    public int insert(Settlement s) {
        s.setId(db.newId());
        db.settlements.add(s);
        return s.getId();
    }

    @Override
    public int insert(Connection c, Settlement s) {
        return insert(s);
    }

    @Override
    public void update(Settlement s) {
        delete(s.getId());
        db.settlements.add(s);
    }

    @Override
    public void update(Connection c, Settlement s) {
        update(s);
    }

    @Override
    public void delete(int id) {
        db.settlements.removeIf(s -> s.getId() == id);
    }

    @Override
    public List<Settlement> findPendingTo(int userId, int householdId) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement s : db.settlements) {
            if (s.isPending() && s.getToUser() == userId && s.getHouseholdId() == householdId) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public List<Settlement> findPendingInHousehold(int householdId) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement s : db.settlements) {
            if (s.isPending() && s.getHouseholdId() == householdId) {
                result.add(s);
            }
        }
        return result;
    }
}
