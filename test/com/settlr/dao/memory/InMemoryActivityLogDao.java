package com.settlr.dao.memory;

import com.settlr.dao.ActivityLogDao;
import com.settlr.model.ActivityLog;

import java.util.ArrayList;
import java.util.List;

/** The activity feed kept in a list, newest last. */
class InMemoryActivityLogDao implements ActivityLogDao {

    private final InMemoryDatabase db;

    InMemoryActivityLogDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public ActivityLog findById(int id) {
        for (ActivityLog a : db.activity) {
            if (a.getId() == id) {
                return a;
            }
        }
        return null;
    }

    @Override
    public List<ActivityLog> findRecent(int householdId, int limit) {
        List<ActivityLog> result = new ArrayList<>();
        for (int i = db.activity.size() - 1; i >= 0 && result.size() < limit; i--) {
            if (db.activity.get(i).getHouseholdId() == householdId) {
                result.add(db.activity.get(i));
            }
        }
        return result;
    }

    @Override
    public int insert(ActivityLog a) {
        a.setId(db.newId());
        db.activity.add(a);
        return a.getId();
    }

    @Override
    public void update(ActivityLog a) {
        // the feed is never edited
    }

    @Override
    public void delete(int id) {
        db.activity.removeIf(a -> a.getId() == id);
    }
}
