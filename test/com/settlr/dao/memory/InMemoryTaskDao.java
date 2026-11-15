package com.settlr.dao.memory;

import com.settlr.dao.TaskDao;
import com.settlr.model.Task;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/** Tasks kept in a list. Deleting archives, like the MySQL version. */
class InMemoryTaskDao implements TaskDao {

    private final InMemoryDatabase db;

    InMemoryTaskDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Task findById(int id) {
        return db.task(id);
    }

    @Override
    public List<Task> findActiveByHousehold(int householdId) {
        List<Task> result = new ArrayList<>();
        for (Task t : db.tasks) {
            if (t.getHouseholdId() == householdId && !t.isArchived()) {
                result.add(t);
            }
        }
        return result;
    }

    @Override
    public List<Task> findAllActive() {
        List<Task> result = new ArrayList<>();
        for (Task t : db.tasks) {
            if (!t.isArchived()) {
                result.add(t);
            }
        }
        return result;
    }

    @Override
    public int insert(Task task) {
        task.setId(db.newId());
        db.tasks.add(task);
        return task.getId();
    }

    @Override
    public int insert(Connection c, Task task) {
        return insert(task);
    }

    @Override
    public void update(Task task) {
        db.tasks.removeIf(t -> t.getId() == task.getId());
        db.tasks.add(task);
    }

    @Override
    public void update(Connection c, Task task) {
        update(task);
    }

    @Override
    public void delete(int id) {
        Task t = db.task(id);
        if (t != null) {
            t.setArchived(true);
        }
    }

    @Override
    public void delete(Connection c, int id) {
        delete(id);
    }
}
