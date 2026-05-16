package com.auction.shared.dto.response;

import com.auction.shared.enums.UserRole;
import java.io.Serializable;

//Phản hồi sau khi thực hiện Đăng nhập hoặc Đăng ký.
public class AuthResponse implements Serializable {
    private String userId;    // SỬA: Long -> String
    private String username;
    private UserRole role;
    private String token;     // Thêm trường token để dùng cho các request sau
    private String fullName;
    private String email;
    private String avatarBase64;

    public AuthResponse() {}

    public AuthResponse(String userId, String username, UserRole role, String token) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.token = token;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarBase64() { return avatarBase64; }
    public void setAvatarBase64(String avatarBase64) { this.avatarBase64 = avatarBase64; }
}
