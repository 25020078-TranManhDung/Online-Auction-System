package com.auction.shared.model.user;
import com.auction.shared.model.entity.Entity;

public abstract class User extends Entity {
    private String username;
    private String password;
    private String email;

    public User(String username, String password, String email) {
        super();
        this.username = username;
        this.password = password;
        this.email = email;
    }

    public boolean login(String inputPassword) {
        return this.password.equals(inputPassword);
    }

    public abstract void showRole();

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
    }
}
