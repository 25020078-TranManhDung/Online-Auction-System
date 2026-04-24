package com.auction.client.model;

public class UserSession {
    private static UserSession instance;


    private String userId;
    private String username;
    private String token;
    private String role;
    private String expiresAt; // Lưu thời gian hết hạn để xử lý tự động đăng xuất nếu cần

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }


    public void initSession(String userId, String username, String token, String role, String expiresAt) {
        this.userId = userId;
        this.username = username;
        this.token = token;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public void cleanUserSession() {
        this.userId = null;
        this.username = null;
        this.token = null;
        this.role = null;
        this.expiresAt = null;
    }

    // Getters đầy đủ
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getToken() { return token; }
    public String getRole() { return role; }
    public String getExpiresAt() { return expiresAt; }
}