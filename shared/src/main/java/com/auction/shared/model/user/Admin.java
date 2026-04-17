package com.auction.shared.model.user;

import com.auction.shared.enums.UserRole;

public class Admin extends User {

    private int adminLevel;

    // Cập nhật Constructor: Thêm String id và truyền UserRole.ADMIN vào super()
    public Admin(String id, String username, String password, String email, int adminLevel) {
        super(id, username, password, email, UserRole.ADMIN);
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
