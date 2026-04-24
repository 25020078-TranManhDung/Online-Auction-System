package com.auction.shared.model.user;

import com.auction.shared.enums.UserRole;

public class Seller extends User {

    // reputationScore (điểm uy tín)
    private double reputationScore;

    // Cập nhật Constructor: Thêm String id và truyền UserRole.SELLER vào super()

    public Seller() {
        super("", "", "", "", UserRole.SELLER);
    }
    public Seller(String id, String username, String password, String email) {
        super(id, username, password, email, UserRole.SELLER);
        this.reputationScore = 5.0; // Điểm mặc định
    }

    @Override
    public void showRole() {
        System.out.println("Vai trò: Người bán hàng (Seller)");
    }

    public double getReputationScore() {
        return reputationScore;
    }

    public void setReputationScore(double reputationScore) {
        this.reputationScore = reputationScore;
    }
}
