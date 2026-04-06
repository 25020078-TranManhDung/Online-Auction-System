package com.auction.shared.dto.response;

import com.auction.shared.enums.UserRole;
import java.io.Serializable;

//Phản hồi sau khi thực hiện Đăng nhập hoặc Đăng ký.
public class AuthResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private Long userId;
    private String username;
    private UserRole role;

    public AuthResponse() {}

    public AuthResponse(boolean success, String message, Long userId, String username, UserRole role) {
        this.success = success;
        this.message = message;
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public UserRole getRole() { return role; }
}
