package com.settlr.dao.memory;

import com.settlr.dao.UserDao;
import com.settlr.model.User;

/** Users kept in a list. */
class InMemoryUserDao implements UserDao {

    private final InMemoryDatabase db;

    InMemoryUserDao(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public User findById(int id) {
        for (User u : db.users) {
            if (u.getId() == id) {
                return u;
            }
        }
        return null;
    }

    @Override
    public User findByUsername(String username) {
        for (User u : db.users) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                return u;
            }
        }
        return null;
    }

    /** Keeps the id the test gave the user, or hands out a new one if it's 0. */
    @Override
    public int insert(User user) {
        if (user.getId() <= 0) {
            user.setId(db.newId());
        }
        db.users.add(user);
        return user.getId();
    }

    @Override
    public void update(User user) {
        delete(user.getId());
        db.users.add(user);
    }

    @Override
    public void delete(int id) {
        db.users.removeIf(u -> u.getId() == id);
    }
}
