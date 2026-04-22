package com.auction.client.model;

public class UserSession {
    private static UserSession instance;

    private String username;
    private String token;
    private String role;

    // Private constructor để ngăn việc tạo đối tượng mới bên ngoài
    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    // Khởi tạo phiên làm việc mới
    public void initSession(String username, String token, String role) {
        this.username = username;
        this.token = token;
        this.role = role;
    }

    // Xóa phiên làm việc khi đăng xuất
    public void cleanUserSession() {
        this.username = null;
        this.token = null;
        this.role = null;
    }

    // Getters
    public String getUsername() { return username; }
    public String getToken() { return token; }
    public String getRole() { return role; }
}