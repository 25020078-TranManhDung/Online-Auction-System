package com.auction.shared.model.user;

import com.auction.shared.model.entity.Entity;
import com.auction.shared.enums.UserRole;

public abstract class User extends Entity {
    private String username;
    private String password;
    private String email;
    private UserRole role; // Bắt buộc để map JSON

    public User(String id, String username, String password, String email, UserRole role) {
        super(id);
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
    }

    public boolean login(String inputPassword) {
        return this.password.equals(inputPassword);
    }

    public abstract void showRole();

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
}