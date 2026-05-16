package com.auction.shared.model.user;

import com.auction.shared.model.entity.Entity;
import com.auction.shared.enums.UserRole;
import java.time.LocalDateTime;

public abstract class User extends Entity {
    private String username;
    private String password;
    private String email;
    private String fullname;   // Họ và tên đầy đủ
    private UserRole role;

    private String status = "ACTIVE";           // ACTIVE | TEMP_LOCKED | PERM_LOCKED
    private int violationCount = 0;             // Số lần vi phạm tích lũy
    private LocalDateTime lockedUntil = null;   // null = ACTIVE hoặc PERM_LOCKED

    // [MỚI] Thêm trường lưu chuỗi mã hóa ảnh đại diện (Base64) hoặc URL
    private String avatar;

    public User() {
        super();
    }

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

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getViolationCount() { return violationCount; }
    public void setViolationCount(int violationCount) { this.violationCount = violationCount; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    // [MỚI] Getter và Setter cho avatar
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}