package com.auction.server.dao;

import com.auction.shared.model.user.User;
import java.util.List;

public interface UserDAO {
    User findById(int id);
    User findByUsername(String username);
    List<User> findAll();

    boolean save(User user);
    boolean update(User user);
    boolean delete(int id);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
