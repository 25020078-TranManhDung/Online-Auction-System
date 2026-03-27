package com.auction.shared.model.user;

public class Admin extends User {

    private int adminLevel;

    public Admin(String username, String password, String email, int adminLevel) {
        super(username, password, email);
        this.adminLevel = adminLevel;
    }

    @Override
    public void showRole() {
        System.out.println("Vai trò: Quản trị viên hệ thống (Admin) - Cấp độ: " + adminLevel);
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(int adminLevel) {
        this.adminLevel = adminLevel;
    }
}
